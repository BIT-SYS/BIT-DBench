/*
 * Copyright 2011 by lichtflut Forschungs- und Entwicklungsgesellschaft mbH
 */
package org.arastreju.bindings.neo4j.index;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

import org.arastreju.sge.model.nodes.ResourceNode;
import org.arastreju.sge.naming.QualifiedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 *  Cache for attached resources.
 * </p>
 *
 * <p>
 * 	Created Dec 23, 2011
 * </p>
 *
 * @author Oliver Tigges
 */
public class AttachedResourcesCache {
	
	private SoftReference<Map<QualifiedName, ResourceNode>> registerReference;
	
	private Logger logger = LoggerFactory.getLogger(AttachedResourcesCache.class);
	
	// ----------------------------------------------------
	
	/**
	 * @param qn The resource's qualified name.
	 * @return The resource or null;
	 */
	public ResourceNode get(QualifiedName qn) {
		 final ResourceNode node = getMap().get(qn);
		 if (node != null && !node.isAttached()) {
			 logger.warn("found detached node in cache: " + node);
			 remove(qn);
			 return null;
		 }
		 return node;
	}
	
	/**
	 * @param qn The resource's qualified name.
	 * @param resource
	 */
	public void put(QualifiedName qn, ResourceNode resource) {
		getMap().put(qn, resource);
	}
	
	/**
	 * @param qn The resource's qualified name.
	 */
	public void remove(QualifiedName qn) {
		getMap().remove(qn);
	}
	
	/**
	 * Clear the cache.
	 */
	public void clear() {
		getMap().clear();
	}
	
	// ----------------------------------------------------
	
	private synchronized Map<QualifiedName, ResourceNode> getMap() {
		if (registerReference == null) {
			final Map<QualifiedName, ResourceNode> map = new HashMap<QualifiedName, ResourceNode>(1000);
			registerReference = new SoftReference<Map<QualifiedName, ResourceNode>>(map);
		}
		return registerReference.get();
	}

}
