/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.ql.capabilities;

public abstract class Resolvables {

    public static boolean resolved(Iterable<? extends Resolvable> resolvables) {
        for (Resolvable resolvable : resolvables) {
            if (!resolvable.resolved()) {
                return false;
            }
        }
        return true;
    }
}
