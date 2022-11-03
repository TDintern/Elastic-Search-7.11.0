/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.rest.action.document;

import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.ActiveShardCount;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.logging.DeprecationLogger;
import org.elasticsearch.index.VersionType;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestActions;
import org.elasticsearch.rest.action.RestStatusToXContentListener;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;

import java.io.IOException;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static org.elasticsearch.rest.RestRequest.Method.POST;

public class RestUpdateAction extends BaseRestHandler {
    private static final DeprecationLogger deprecationLogger =
            DeprecationLogger.getLogger(RestUpdateAction.class);
    public static final String TYPES_DEPRECATION_MESSAGE = "[types removal] Specifying types in " +
        "document update requests is deprecated, use the endpoint /{index}/_update/{id} instead.";

    @Override
    public List<Route> routes() {
        return unmodifiableList(asList(
            new Route(POST, "/{index}/_update/{id}"),
            // Deprecated typed endpoint.
            new Route(POST, "/{index}/{type}/{id}/_update")));
    }

    @Override
    public String getName() {
        return "document_update_action";
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
        UpdateRequest updateRequest;
        if (request.hasParam("type")) {
            deprecationLogger.deprecate("update_with_types", TYPES_DEPRECATION_MESSAGE);
            updateRequest = new UpdateRequest(request.param("index"),
                request.param("type"),
                request.param("id"));
        } else {
            updateRequest = new UpdateRequest(request.param("index"), request.param("id"));
        }

        updateRequest.routing(request.param("routing"));
        updateRequest.timeout(request.paramAsTime("timeout", updateRequest.timeout()));
        updateRequest.setRefreshPolicy(request.param("refresh"));
        String waitForActiveShards = request.param("wait_for_active_shards");
        if (waitForActiveShards != null) {
            updateRequest.waitForActiveShards(ActiveShardCount.parseString(waitForActiveShards));
        }
        updateRequest.docAsUpsert(request.paramAsBoolean("doc_as_upsert", updateRequest.docAsUpsert()));
        FetchSourceContext fetchSourceContext = FetchSourceContext.parseFromRestRequest(request);
        if (fetchSourceContext != null) {
            updateRequest.fetchSource(fetchSourceContext);
        }

        updateRequest.retryOnConflict(request.paramAsInt("retry_on_conflict", updateRequest.retryOnConflict()));
        if (request.hasParam("version") || request.hasParam("version_type")) {
            final ActionRequestValidationException versioningError = new ActionRequestValidationException();
            versioningError.addValidationError("internal versioning can not be used for optimistic concurrency control. " +
                "Please use `if_seq_no` and `if_primary_term` instead");
            throw versioningError;
        }

        updateRequest.setIfSeqNo(request.paramAsLong("if_seq_no", updateRequest.ifSeqNo()));
        updateRequest.setIfPrimaryTerm(request.paramAsLong("if_primary_term", updateRequest.ifPrimaryTerm()));
        updateRequest.setRequireAlias(request.paramAsBoolean(DocWriteRequest.REQUIRE_ALIAS, updateRequest.isRequireAlias()));

        request.applyContentParser(parser -> {
            updateRequest.fromXContent(parser);
            IndexRequest upsertRequest = updateRequest.upsertRequest();
            if (upsertRequest != null) {
                upsertRequest.routing(request.param("routing"));
                upsertRequest.version(RestActions.parseVersion(request));
                upsertRequest.versionType(VersionType.fromString(request.param("version_type"), upsertRequest.versionType()));
            }
            IndexRequest doc = updateRequest.doc();
            if (doc != null) {
                doc.routing(request.param("routing"));
                doc.version(RestActions.parseVersion(request));
                doc.versionType(VersionType.fromString(request.param("version_type"), doc.versionType()));
            }
        });

        return channel ->
                client.update(updateRequest, new RestStatusToXContentListener<>(channel, r -> r.getLocation(updateRequest.routing())));
    }

}
