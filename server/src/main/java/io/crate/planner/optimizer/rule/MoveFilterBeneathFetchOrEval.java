/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package io.crate.planner.optimizer.rule;

import io.crate.expression.symbol.Symbol;
import io.crate.metadata.NodeContext;
import io.crate.metadata.TransactionContext;
import io.crate.planner.operators.Eval;
import io.crate.planner.operators.Filter;
import io.crate.planner.operators.LogicalPlan;
import io.crate.planner.optimizer.Rule;
import io.crate.planner.optimizer.matcher.Capture;
import io.crate.planner.optimizer.matcher.Captures;
import io.crate.planner.optimizer.matcher.Pattern;
import io.crate.statistics.TableStats;

import java.util.List;

import static io.crate.planner.operators.LogicalPlanner.extractColumns;
import static io.crate.planner.optimizer.matcher.Pattern.typeOf;
import static io.crate.planner.optimizer.matcher.Patterns.source;
import static io.crate.planner.optimizer.rule.Util.transpose;

public final class MoveFilterBeneathFetchOrEval implements Rule<Filter> {

    private final Capture<Eval> fetchOrEvalCapture;
    private final Pattern<Filter> pattern;

    public MoveFilterBeneathFetchOrEval() {
        this.fetchOrEvalCapture = new Capture<>();
        this.pattern = typeOf(Filter.class)
            .with(source(), typeOf(Eval.class).capturedAs(fetchOrEvalCapture));
    }

    @Override
    public Pattern<Filter> pattern() {
        return pattern;
    }

    @Override
    public LogicalPlan apply(Filter plan, Captures captures, TableStats tableStats, TransactionContext txnCtx, NodeContext nodeCtx) {
        Eval eval = captures.get(fetchOrEvalCapture);
        List<Symbol> outputsOfFetchSource = eval.source().outputs();
        if (outputsOfFetchSource.containsAll(extractColumns(plan.query()))) {
            return transpose(plan, eval);
        }
        return null;
    }
}
