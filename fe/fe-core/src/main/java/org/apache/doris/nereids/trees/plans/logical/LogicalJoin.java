// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.doris.nereids.trees.plans.logical;

import org.apache.doris.nereids.memo.GroupExpression;
import org.apache.doris.nereids.properties.LogicalProperties;
import org.apache.doris.nereids.rules.exploration.join.JoinReorderContext;
import org.apache.doris.nereids.trees.expressions.Expression;
import org.apache.doris.nereids.trees.expressions.Slot;
import org.apache.doris.nereids.trees.plans.JoinHint;
import org.apache.doris.nereids.trees.plans.JoinType;
import org.apache.doris.nereids.trees.plans.Plan;
import org.apache.doris.nereids.trees.plans.PlanType;
import org.apache.doris.nereids.trees.plans.algebra.Join;
import org.apache.doris.nereids.trees.plans.visitor.PlanVisitor;
import org.apache.doris.nereids.util.ExpressionUtils;
import org.apache.doris.nereids.util.Utils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Logical join plan.
 */
public class LogicalJoin<LEFT_CHILD_TYPE extends Plan, RIGHT_CHILD_TYPE extends Plan>
        extends LogicalBinary<LEFT_CHILD_TYPE, RIGHT_CHILD_TYPE> implements Join {

    private final JoinType joinType;
    private final List<Expression> otherJoinConjuncts;
    private final List<Expression> hashJoinConjuncts;
    private final JoinHint hint;

    // Use for top-to-down join reorder
    private final JoinReorderContext joinReorderContext = new JoinReorderContext();

    /**
     * Constructor for LogicalJoinPlan.
     *
     * @param joinType logical type for join
     */
    public LogicalJoin(JoinType joinType, LEFT_CHILD_TYPE leftChild, RIGHT_CHILD_TYPE rightChild) {
        this(joinType, ExpressionUtils.EMPTY_CONDITION, ExpressionUtils.EMPTY_CONDITION, JoinHint.NONE,
                Optional.empty(), Optional.empty(), leftChild, rightChild, JoinReorderContext.EMPTY);
    }

    public LogicalJoin(JoinType joinType, List<Expression> hashJoinConjuncts, LEFT_CHILD_TYPE leftChild,
            RIGHT_CHILD_TYPE rightChild) {
        this(joinType, hashJoinConjuncts, ExpressionUtils.EMPTY_CONDITION, JoinHint.NONE, Optional.empty(),
                Optional.empty(), leftChild, rightChild, JoinReorderContext.EMPTY);
    }

    public LogicalJoin(
            JoinType joinType,
            List<Expression> hashJoinConjuncts,
            List<Expression> otherJoinConjuncts,
            JoinHint hint,
            LEFT_CHILD_TYPE leftChild, RIGHT_CHILD_TYPE rightChild) {
        this(joinType, hashJoinConjuncts,
                otherJoinConjuncts, hint, Optional.empty(), Optional.empty(), leftChild, rightChild,
                JoinReorderContext.EMPTY);
    }

    public LogicalJoin(
            JoinType joinType,
            List<Expression> hashJoinConjuncts,
            JoinHint hint,
            LEFT_CHILD_TYPE leftChild,
            RIGHT_CHILD_TYPE rightChild,
            JoinReorderContext joinReorderContext) {
        this(joinType, hashJoinConjuncts, ExpressionUtils.EMPTY_CONDITION, hint,
                Optional.empty(), Optional.empty(), leftChild, rightChild, joinReorderContext);
    }

    public LogicalJoin(
            JoinType joinType,
            List<Expression> hashJoinConjuncts,
            List<Expression> otherJoinConjuncts,
            JoinHint hint,
            LEFT_CHILD_TYPE leftChild,
            RIGHT_CHILD_TYPE rightChild,
            JoinReorderContext joinReorderContext) {
        this(joinType, hashJoinConjuncts, otherJoinConjuncts, hint,
                Optional.empty(), Optional.empty(), leftChild, rightChild, joinReorderContext);
    }

    /**
     * Constructor for LogicalJoinPlan.
     */
    public LogicalJoin(
            JoinType joinType,
            List<Expression> hashJoinConjuncts,
            List<Expression> otherJoinConjuncts,
            JoinHint hint,
            Optional<GroupExpression> groupExpression,
            Optional<LogicalProperties> logicalProperties,
            LEFT_CHILD_TYPE leftChild,
            RIGHT_CHILD_TYPE rightChild,
            JoinReorderContext joinReorderContext) {
        super(PlanType.LOGICAL_JOIN, groupExpression, logicalProperties, leftChild, rightChild);
        this.joinType = Objects.requireNonNull(joinType, "joinType can not be null");
        this.hashJoinConjuncts = ImmutableList.copyOf(hashJoinConjuncts);
        this.otherJoinConjuncts = ImmutableList.copyOf(otherJoinConjuncts);
        this.hint = Objects.requireNonNull(hint, "hint can not be null");
        this.joinReorderContext.copyFrom(joinReorderContext);
    }

    public List<Expression> getOtherJoinConjuncts() {
        return otherJoinConjuncts;
    }

    @Override
    public List<Expression> getHashJoinConjuncts() {
        return hashJoinConjuncts;
    }

    public Optional<Expression> getOnClauseCondition() {
        return ExpressionUtils.optionalAnd(hashJoinConjuncts, otherJoinConjuncts);
    }

    public JoinType getJoinType() {
        return joinType;
    }

    public JoinHint getHint() {
        return hint;
    }

    public JoinReorderContext getJoinReorderContext() {
        return joinReorderContext;
    }

    @Override
    public List<Slot> computeOutput() {

        List<Slot> newLeftOutput = left().getOutput().stream().map(o -> o.withNullable(true))
                .collect(ImmutableList.toImmutableList());

        List<Slot> newRightOutput = right().getOutput().stream().map(o -> o.withNullable(true))
                .collect(ImmutableList.toImmutableList());

        switch (joinType) {
            case LEFT_SEMI_JOIN:
            case LEFT_ANTI_JOIN:
            case NULL_AWARE_LEFT_ANTI_JOIN:
                return ImmutableList.copyOf(left().getOutput());
            case RIGHT_SEMI_JOIN:
            case RIGHT_ANTI_JOIN:
                return ImmutableList.copyOf(right().getOutput());
            case LEFT_OUTER_JOIN:
                return ImmutableList.<Slot>builder()
                        .addAll(left().getOutput())
                        .addAll(newRightOutput)
                        .build();
            case RIGHT_OUTER_JOIN:
                return ImmutableList.<Slot>builder()
                        .addAll(newLeftOutput)
                        .addAll(right().getOutput())
                        .build();
            case FULL_OUTER_JOIN:
                return ImmutableList.<Slot>builder()
                        .addAll(newLeftOutput)
                        .addAll(newRightOutput)
                        .build();
            default:
                return ImmutableList.<Slot>builder()
                        .addAll(left().getOutput())
                        .addAll(right().getOutput())
                        .build();
        }
    }

    @Override
    public String toString() {
        List<Object> args = Lists.newArrayList(
                "type", joinType,
                "hashJoinConjuncts", hashJoinConjuncts,
                "otherJoinConjuncts", otherJoinConjuncts);
        if (hint != JoinHint.NONE) {
            args.add("hint");
            args.add(hint);
        }
        return Utils.toSqlString("LogicalJoin", args.toArray());
    }

    // TODO:
    // 1. consider the order of conjuncts in otherJoinConjuncts and hashJoinConjuncts
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LogicalJoin that = (LogicalJoin) o;

        return joinType == that.joinType
                // TODO: why use containsAll?
                && that.getHashJoinConjuncts().containsAll(hashJoinConjuncts)
                && hashJoinConjuncts.containsAll(that.getHashJoinConjuncts())
                && Objects.equals(otherJoinConjuncts, that.otherJoinConjuncts)
                && hint.equals(that.hint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(joinType, hashJoinConjuncts, otherJoinConjuncts);
    }

    @Override
    public <R, C> R accept(PlanVisitor<R, C> visitor, C context) {
        return visitor.visitLogicalJoin(this, context);
    }

    @Override
    public List<? extends Expression> getExpressions() {
        return new Builder<Expression>()
                .addAll(hashJoinConjuncts)
                .addAll(otherJoinConjuncts)
                .build();
    }

    @Override
    public LEFT_CHILD_TYPE left() {
        return (LEFT_CHILD_TYPE) child(0);
    }

    @Override
    public RIGHT_CHILD_TYPE right() {
        return (RIGHT_CHILD_TYPE) child(1);
    }

    @Override
    public LogicalJoin<Plan, Plan> withChildren(List<Plan> children) {
        Preconditions.checkArgument(children.size() == 2);
        return new LogicalJoin<>(joinType, hashJoinConjuncts, otherJoinConjuncts, hint, children.get(0),
                children.get(1), joinReorderContext);
    }

    @Override
    public LogicalJoin<Plan, Plan> withGroupExpression(Optional<GroupExpression> groupExpression) {
        return new LogicalJoin<>(joinType, hashJoinConjuncts, otherJoinConjuncts, hint, groupExpression,
                Optional.of(getLogicalProperties()), left(), right(), joinReorderContext);
    }

    @Override
    public LogicalJoin<Plan, Plan> withLogicalProperties(Optional<LogicalProperties> logicalProperties) {
        return new LogicalJoin<>(joinType, hashJoinConjuncts, otherJoinConjuncts, hint,
                Optional.empty(), logicalProperties, left(), right(), joinReorderContext);
    }

    public LogicalJoin<Plan, Plan> withHashJoinConjuncts(List<Expression> hashJoinConjuncts) {
        return new LogicalJoin<>(
                joinType, hashJoinConjuncts, this.otherJoinConjuncts, hint, left(), right(), joinReorderContext);
    }

    public LogicalJoin<Plan, Plan> withJoinConjuncts(
            List<Expression> hashJoinConjuncts, List<Expression> otherJoinConjuncts) {
        return new LogicalJoin<>(joinType, hashJoinConjuncts, otherJoinConjuncts,
                hint, left(), right(), joinReorderContext);
    }

    public LogicalJoin<Plan, Plan> withHashJoinConjunctsAndChildren(
            List<Expression> hashJoinConjuncts, List<Plan> children) {
        Preconditions.checkArgument(children.size() == 2);
        return new LogicalJoin<>(joinType, hashJoinConjuncts, otherJoinConjuncts, hint, children.get(0),
                children.get(1), joinReorderContext);
    }

    public LogicalJoin<Plan, Plan> withJoinType(JoinType joinType) {
        return new LogicalJoin<>(joinType, hashJoinConjuncts, otherJoinConjuncts, hint, left(), right(),
                joinReorderContext);
    }

    public LogicalJoin<Plan, Plan> withOtherJoinConjuncts(List<Expression> otherJoinConjuncts) {
        return new LogicalJoin<>(joinType, hashJoinConjuncts, otherJoinConjuncts, hint, left(), right(),
                joinReorderContext);
    }
}
