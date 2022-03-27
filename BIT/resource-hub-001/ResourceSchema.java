/*
 * Copyright (C) 2011 lichtflut Forschungs- und Entwicklungsgesellschaft mbH
 */
package de.lichtflut.rb.core.schema.model;

import java.io.Serializable;
import java.util.List;

import org.arastreju.sge.model.ResourceID;

import de.lichtflut.rb.core.common.EntityLabelBuilder;

/**
 * <p>
 *  Schema for a Resource Type, e.g. a person, an organization or a project.
 * </p>
 *
 * <p>
 * 	Created Jan 27, 2011
 * </p>
 *
 * @author Oliver Tigges
 */
public interface ResourceSchema extends Serializable {

	/**
	 * The ResourceID  of the Type described by this schema.
	 * @return The ID of the described Resource Type.
	 */
	ResourceID getDescribedType();

	// -----------------------------------------------------

	/**
	 * Get all {@link PropertyDeclaration}s that represent the quickInfo.
	 * @return a {@link List} of {@link PropertyDeclaration}s
	 */
	List<PropertyDeclaration> getQuickInfo();

	/**
	 * Get all the schema's {@link PropertyDeclaration} as list.
	 * @return all schema {@link PropertyDeclaration} as list.
	 */
	List<PropertyDeclaration> getPropertyDeclarations();

	/**
	 * Add a additional {@link PropertyDeclaration} to the ResourceSchema.
	 * @param declaration The declaration to set.
	 */
	void addPropertyDeclaration(PropertyDeclaration declaration);

	/**
	 * Removes a {@link PropertyDeclaration}. <br />
	 * If no entry exists for the given declaration, no action will be taken.
	 * @param declaration The declaration to be removed
	 */
	void removePropertyDeclaration(PropertyDeclaration declaration);

	// -----------------------------------------------------

	/**
	 * The label builder can create dynamic labels for an entity belonging to this schema.
	 * @return The label builder for this schema.
	 */
	EntityLabelBuilder getLabelBuilder();

}
