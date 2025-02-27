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

package org.apache.doris.nereids.trees.expressions;

import org.apache.doris.analysis.ArithmeticExpr.Operator;
import org.apache.doris.nereids.trees.expressions.visitor.ExpressionVisitor;
import org.apache.doris.nereids.types.DataType;
import org.apache.doris.nereids.types.coercion.AbstractDataType;
import org.apache.doris.nereids.types.coercion.NumericType;
import org.apache.doris.nereids.util.TypeCoercionUtils;

import com.google.common.base.Preconditions;

import java.util.List;

/**
 * Multiply Expression.
 */
public class Multiply extends BinaryArithmetic {

    public Multiply(Expression left, Expression right) {
        super(left, right, Operator.MULTIPLY);
    }

    @Override
    public Expression withChildren(List<Expression> children) {
        Preconditions.checkArgument(children.size() == 2);
        return new Multiply(children.get(0), children.get(1));
    }

    @Override
    public DataType getDataType() {
        DataType rightType = child(0).getDataType();
        DataType leftType = child(1).getDataType();
        DataType outputType = TypeCoercionUtils.findTightestCommonType(this,
                rightType, leftType).orElseGet(() -> rightType);
        outputType = outputType.promotion();
        return outputType;
    }

    @Override
    public <R, C> R accept(ExpressionVisitor<R, C> visitor, C context) {
        return visitor.visitMultiply(this, context);
    }

    @Override
    public AbstractDataType inputType() {
        return NumericType.INSTANCE;
    }
}
