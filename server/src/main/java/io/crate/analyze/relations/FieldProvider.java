/*
 * Licensed to Crate.io GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.analyze.relations;

import io.crate.expression.symbol.Literal;
import io.crate.expression.symbol.Symbol;
import io.crate.metadata.ColumnIdent;
import io.crate.metadata.table.Operation;
import io.crate.sql.tree.QualifiedName;

import javax.annotation.Nullable;
import java.util.List;

public interface FieldProvider<T extends Symbol> {

    T resolveField(QualifiedName qualifiedName,
                   @Nullable List<String> path,
                   Operation operation,
                   boolean errorOnUnknownObjectKey);

    FieldProvider<?> UNSUPPORTED = (qualifiedName, path, operation, errorOnUnknownObjectKey) -> {
        throw new UnsupportedOperationException(
            "Columns cannot be used in this context. " +
            "Maybe you wanted to use a string literal which requires single quotes: '" + qualifiedName + "'");
    };

    FieldProvider<Literal<?>> FIELDS_AS_LITERAL = ((qualifiedName, path, operation, errorOnUnknownObjectKey) -> Literal.of(new ColumnIdent(qualifiedName.toString(), path).fqn()));
}
