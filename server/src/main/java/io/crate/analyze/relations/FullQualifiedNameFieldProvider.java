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

import io.crate.exceptions.AmbiguousColumnException;
import io.crate.exceptions.ColumnUnknownException;
import io.crate.exceptions.RelationUnknown;
import io.crate.expression.symbol.Symbol;
import io.crate.metadata.ColumnIdent;
import io.crate.metadata.RelationName;
import io.crate.metadata.table.Operation;
import io.crate.sql.tree.QualifiedName;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves QualifiedNames to Fields considering multiple AnalyzedRelations.
 * <p>
 * The Resolver also takes full qualified names so the name may contain table
 * and / or schema.
 */
public class FullQualifiedNameFieldProvider implements FieldProvider<Symbol> {

    private final Map<RelationName, AnalyzedRelation> sources;
    private final ParentRelations parents;
    private final String defaultSchema;

    public FullQualifiedNameFieldProvider(Map<RelationName, AnalyzedRelation> sources,
                                          ParentRelations parents,
                                          String defaultSchema) {
        this.sources = Objects.requireNonNull(sources, "Please provide a source map.");
        this.parents = Objects.requireNonNull(parents, "ParentRelations must not be null");
        this.defaultSchema = Objects.requireNonNull(defaultSchema, "Default schema must not be null");
    }

    @Override
    public Symbol resolveField(QualifiedName qualifiedName, @Nullable List<String> path, Operation operation, boolean errorOnUnknownObjectKey) {
        List<String> parts = qualifiedName.getParts();
        String columnSchema = null;
        String columnTableName = null;
        ColumnIdent columnIdent = new ColumnIdent(parts.get(parts.size() - 1), path);
        switch (parts.size()) {
            case 1:
                break;
            case 2:
                columnTableName = parts.get(0);
                break;
            case 3:
                columnSchema = parts.get(0);
                columnTableName = parts.get(1);
                break;
            default:
                throw new IllegalArgumentException("Column reference \"%s\" has too many parts. " +
                                                   "A column reference can have at most 3 parts and must have one of the following formats:  " +
                                                   "\"<column>\", \"<table>.<column>\" or \"<schema>.<table>.<column>\"");
        }

        boolean schemaMatched = false;
        boolean tableNameMatched = false;
        Symbol lastField = null;

        for (var entry : sources.entrySet()) {
            RelationName relName = entry.getKey();
            String sourceSchema = relName.schema();
            String sourceTableOrAlias = relName.name();

            if (columnSchema != null && !columnSchema.equals(sourceSchema)) {
                continue;
            }
            schemaMatched = true;
            if (columnTableName != null && !sourceTableOrAlias.equals(columnTableName)) {
                continue;
            }
            tableNameMatched = true;

            AnalyzedRelation sourceRelation = entry.getValue();
            Symbol newField = sourceRelation.getField(columnIdent, operation, errorOnUnknownObjectKey);
            if (newField != null) {
                if (lastField != null) {
                    if (errorOnUnknownObjectKey == false) {
                        /* ex) CREATE TABLE c1 (obj object as (x int));
                         *     CREATE TABLE c2 (obj object as (y int));
                         *     select obj['x'] from c1, c2;
                         *     --> ambiguous because c2.obj['x'] is another candidate with errorOnUnknownObjectKey = false
                         */
                        return resolveField(qualifiedName, path, operation, true);
                    }
                    throw new AmbiguousColumnException(columnIdent, newField);
                }
                lastField = newField;
            }
        }
        if (lastField == null) {
            if (!schemaMatched || !tableNameMatched) {
                String schema = columnSchema == null ? defaultSchema : columnSchema;
                raiseUnsupportedFeatureIfInParentScope(columnSchema, columnTableName, schema);
                RelationName relationName = new RelationName(schema, columnTableName);
                throw new RelationUnknown(relationName);
            }
            RelationName relationName = sources.entrySet().iterator().next().getKey();
            throw new ColumnUnknownException(columnIdent.sqlFqn(), relationName);
        }
        return lastField;
    }

    private void raiseUnsupportedFeatureIfInParentScope(String columnSchema, String columnTableName, String schema) {
        RelationName name = new RelationName(schema, columnTableName);
        if (parents.containsRelation(name)) {
            throw new UnsupportedOperationException(String.format(Locale.ENGLISH,
                "Cannot use relation \"%s.%s\" in this context. It is only accessible in the parent context.",
                schema,
                columnTableName));
        }
        if (columnSchema == null && parents.containsRelation(new RelationName(null, columnTableName))) {
            throw new UnsupportedOperationException(String.format(Locale.ENGLISH,
                "Cannot use relation \"%s\" in this context. It is only accessible in the parent context.", columnTableName));
        }
    }
}
