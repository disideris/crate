/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.action.admin.indices.template.delete;

import org.apache.logging.log4j.message.ParameterizedMessage;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.action.support.master.TransportMasterNodeAction;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.metadata.MetadataIndexTemplateService;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.io.IOException;

/**
 * Delete index action.
 */
public class TransportDeleteIndexTemplateAction extends TransportMasterNodeAction<DeleteIndexTemplateRequest, AcknowledgedResponse> {

    private final MetadataIndexTemplateService indexTemplateService;

    @Inject
    public TransportDeleteIndexTemplateAction(TransportService transportService,
                                              ClusterService clusterService,
                                              ThreadPool threadPool,
                                              MetadataIndexTemplateService indexTemplateService,
                                              IndexNameExpressionResolver indexNameExpressionResolver) {
        super(DeleteIndexTemplateAction.NAME, transportService, clusterService, threadPool, DeleteIndexTemplateRequest::new, indexNameExpressionResolver);
        this.indexTemplateService = indexTemplateService;
    }

    @Override
    protected String executor() {
        // we go async right away
        return ThreadPool.Names.SAME;
    }

    @Override
    protected AcknowledgedResponse read(StreamInput in) throws IOException {
        return new AcknowledgedResponse(in);
    }

    @Override
    protected ClusterBlockException checkBlock(DeleteIndexTemplateRequest request, ClusterState state) {
        return state.blocks().globalBlockedException(ClusterBlockLevel.METADATA_WRITE);
    }

    @Override
    protected void masterOperation(final DeleteIndexTemplateRequest request,
                                   final ClusterState state,
                                   final ActionListener<AcknowledgedResponse> listener) {
        indexTemplateService.removeTemplates(new MetadataIndexTemplateService.RemoveRequest(request.name()).masterTimeout(request.masterNodeTimeout()), new MetadataIndexTemplateService.RemoveListener() {
            @Override
            public void onResponse(MetadataIndexTemplateService.RemoveResponse response) {
                listener.onResponse(new AcknowledgedResponse(response.acknowledged()));
            }

            @Override
            public void onFailure(Exception e) {
                logger.debug(() -> new ParameterizedMessage("failed to delete templates [{}]", request.name()), e);
                listener.onFailure(e);
            }
        });
    }
}
