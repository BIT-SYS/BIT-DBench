/*
 * Copyright 2011 by lichtflut Forschungs- und Entwicklungsgesellschaft mbH
 */
package de.lichtflut.rb.core.services.impl;

import de.lichtflut.infra.exceptions.NotYetSupportedException;
import de.lichtflut.rb.core.RBSystem;
import de.lichtflut.rb.core.eh.ErrorCodes;
import de.lichtflut.rb.core.schema.RBSchema;
import de.lichtflut.rb.core.schema.model.Constraint;
import de.lichtflut.rb.core.schema.model.Datatype;
import de.lichtflut.rb.core.schema.model.PropertyDeclaration;
import de.lichtflut.rb.core.schema.model.ResourceSchema;
import de.lichtflut.rb.core.schema.model.impl.ConstraintImpl;
import de.lichtflut.rb.core.schema.parser.impl.rsf.RsfSchemaParser;
import de.lichtflut.rb.core.schema.persistence.SNConstraint;
import de.lichtflut.rb.core.schema.persistence.SNPropertyDeclaration;
import de.lichtflut.rb.core.schema.persistence.SNResourceSchema;
import de.lichtflut.rb.core.schema.persistence.Schema2GraphBinding;
import de.lichtflut.rb.core.schema.writer.rsf.RsfWriter;
import de.lichtflut.rb.core.services.ConversationFactory;
import de.lichtflut.rb.core.services.SchemaExporter;
import de.lichtflut.rb.core.services.SchemaImporter;
import de.lichtflut.rb.core.services.SchemaManager;
import org.apache.commons.lang3.Validate;
import org.arastreju.sge.Conversation;
import org.arastreju.sge.SNOPS;
import org.arastreju.sge.apriori.RDF;
import org.arastreju.sge.apriori.RDFS;
import org.arastreju.sge.model.ResourceID;
import org.arastreju.sge.model.nodes.ResourceNode;
import org.arastreju.sge.model.nodes.SemanticNode;
import org.arastreju.sge.model.nodes.views.SNProperty;
import org.arastreju.sge.naming.QualifiedName;
import org.arastreju.sge.persistence.TransactionControl;
import org.arastreju.sge.query.Query;
import org.arastreju.sge.query.QueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>
 * 	Implementation of {@link SchemaManager}.
 * </p>
 * 
 * Created: Apr 19, 2011
 * 
 * @author Nils Bleisch
 * @author Oliver Tigges
 */
public class SchemaManagerImpl implements SchemaManager {

	private static final Logger LOGGER = LoggerFactory.getLogger(SchemaManagerImpl.class);

	private final Schema2GraphBinding binding;

	private final ConversationFactory conversationFactory;

	// ---------------- Constructor -------------------------

	/**
	 * Constructor.
	 * @param arastrejuFactory The factory for conversations.
	 */
	public SchemaManagerImpl(final ConversationFactory arastrejuFactory) {
		this.conversationFactory = arastrejuFactory;
		this.binding = new Schema2GraphBinding();
	}

	// -----------------------------------------------------

	@Override
	public ResourceSchema findSchemaForType(final ResourceID type) {
		final SNResourceSchema schemaNode = findSchemaNodeByType(type);
		if (schemaNode != null) {
			return binding.toModelObject(schemaNode);
		} else {
			return null;
		}
	}

	@Override
	public boolean isSchemaDefinedFor(final ResourceID type) {
		return findSchemaNodeByType(type) != null;
	}

	@Override
	public Constraint findConstraint(final QualifiedName qn) {
		final ResourceNode node = conversation().findResource(qn);
		if (node != null) {
			return binding.toModelObject(new SNConstraint(node));
		} else {
			return null;
		}
	}

	@Override
	public Collection<ResourceSchema> findAllResourceSchemas() {
		final List<ResourceSchema> result = new ArrayList<ResourceSchema>();
		final List<ResourceNode> nodes = findResourcesByType(RBSchema.RESOURCE_SCHEMA);
		for (ResourceNode node : nodes) {
			final ResourceSchema schema = binding.toModelObject(SNResourceSchema.view(node));
			result.add(schema);
		}
		return result;
	}

	@Override
	public Collection<Constraint> findPublicConstraints() {
		final List<Constraint> result = new ArrayList<Constraint>();
		final List<ResourceNode> nodes = findResourcesByType(RBSchema.PUBLIC_CONSTRAINT);
		for (ResourceNode node : nodes) {
			final Constraint constraint = binding.toModelObject(new SNConstraint(node));
			if (constraint.isPublic()) {
				result.add(constraint);
			}
		}
		return result;
	}

	// -----------------------------------------------------

	@Override
	public void store(final ResourceSchema schema) {
		Validate.isTrue(schema.getDescribedType() != null, "The type described by this schema is not defined.");
		final Conversation mc = conversation();
		final TransactionControl tx = mc.beginTransaction();
		try {
			final SNResourceSchema existing = findSchemaNodeByType(schema.getDescribedType());
			if (existing != null) {
				removeSchema(mc, existing);
			}
			ensureReferencedResourcesExist(mc, schema);
			final SNResourceSchema node = binding.toSemanticNode(schema);
			mc.attach(node);
			LOGGER.info("Stored schema for type {}.", schema.getDescribedType());
			tx.success();
		} finally {
			tx.finish();
		}
	}

	public Map<Integer, List<PropertyDeclaration>> validate(final ResourceSchema schema){
		Map<Integer, List<PropertyDeclaration>> errors = new HashMap<Integer, List<PropertyDeclaration>>();
		for (PropertyDeclaration decl : schema.getPropertyDeclarations()) {
			validateSingleProperty(decl, errors);
		}
		return errors;
	}

	@Override
	public void removeSchemaForType(final ResourceID type) {
		final SNResourceSchema existing = findSchemaNodeByType(type);
		if (existing != null) {
			removeSchema(conversation(), existing);
			LOGGER.info("Removed schema for type {}.", type);
		}
	}

	@Override
	public void store(final Constraint constraint) {
		Validate.isTrue(constraint.isPublic(), "Only public type definition may be stored explicitly.");
		remove(constraint);
		SNConstraint sn = binding.toSemanticNode(constraint);
		conversation().attach(sn);
		LOGGER.info("Stored public constraint for {}.", constraint.getName());
	}

	@Override
	public void remove(final Constraint constraint){
		final Conversation mc = conversation();
		final ResourceNode existing = mc.findResource(constraint.getQualifiedName());
		if(null != existing){
			mc.remove(existing);
		}
	}

	@Override
	public Constraint prepareConstraint(final QualifiedName qn, final String displayName) {
		final ConstraintImpl constraint = new ConstraintImpl(qn);
		constraint.setApplicableDatatypes(Collections.singletonList(Datatype.STRING));
		constraint.setName(displayName);
		constraint.setPublic(true);
		constraint.setLiteralConstraint("*");
		store(constraint);
		return constraint;
	}

	// -----------------------------------------------------

	@Override
	public SchemaImporter getImporter(final String format) {
		if ("RSF".equalsIgnoreCase(format.trim())) {
			return new SchemaImporterImpl(this, conversation(), new RsfSchemaParser());
		} else {
			throw new NotYetSupportedException("Unsupported format: " + format);
		}
	}

	@Override
	public SchemaExporter getExporter(final String format) {
		if ("RSF".equalsIgnoreCase(format.trim())) {
			return new SchemaExporterImpl(this, new RsfWriter());
		} else {
			throw new NotYetSupportedException("Unsupported format: " + format);
		}
	}

	// -----------------------------------------------------

	protected List<ResourceNode> findResourcesByType(final ResourceID type) {
		final Query query = query().addField(RDF.TYPE, type);
		return query.getResult().toList(2000);
	}

	/**
	 * Find the persistent node representing the schema of the given type.
	 */
	private SNResourceSchema findSchemaNodeByType(final ResourceID type) {
		final Query query = query().addField(RBSchema.DESCRIBES, type);
		final QueryResult result = query.getResult();
		if (result.isEmpty()) {
			return null;
		} else if (result.size() == 1) {
			return SNResourceSchema.view(result.iterator().next());
		} else {
			throw new IllegalStateException("Found more than one Schema for type " + type + ": " + result);
		}
	}

	/**
	 * Removes the schema graph.
	 * @param mc The existing conversation.
	 * @param schemaNode The schema node.
	 */
	protected void removeSchema(final Conversation mc, final SNResourceSchema schemaNode) {
		for(SNPropertyDeclaration decl : schemaNode.getPropertyDeclarations()) {
			if (decl.hasConstraint() && !decl.getConstraint().isPublic()) {
				mc.remove(SNOPS.id(decl.getConstraint().getQualifiedName()));
			}
			mc.remove(decl);
		}
		mc.remove(schemaNode);
	}

	/**
	 * Checks if the resources referenced by this schema exist and have the correct settings:
	 * <ul>
	 * 	<li>Described Type</li>
	 * 	<li>Properties of Property Declarations</li>
	 * </ul>
	 */
	private void ensureReferencedResourcesExist(final Conversation mc, final ResourceSchema schema) {
		// 1st: check described type
		final ResourceNode attached = mc.resolve(schema.getDescribedType());
		final Set<SemanticNode> clazzes = SNOPS.objects(attached, RDF.TYPE);
		if (!clazzes.contains(RBSystem.TYPE)) {
			SNOPS.associate(attached, RDF.TYPE, RBSystem.TYPE);
		}
		if (!clazzes.contains(RDFS.CLASS)) {
			SNOPS.associate(attached, RDF.TYPE, RDFS.CLASS);
		}
		// 2nd: check properties
		for (PropertyDeclaration decl : schema.getPropertyDeclarations()) {
			final SNProperty property = SNProperty.from(mc.resolve(decl.getPropertyDescriptor()));
			if (!property.isSubPropertyOf(RDF.PROPERTY)) {
				SNOPS.associate(property, RDF.TYPE, RDF.PROPERTY);
			}
		}
	}

	private Conversation conversation() {
		return conversationFactory.getConversation(RBSystem.TYPE_SYSTEM_CTX);
	}

	private Query query() {
		return conversation().createQuery();
	}

	/**
	 * Validates a single PropertyDeclarations
	 * @param decl
	 * @param errors
	 */
	private void validateSingleProperty(final PropertyDeclaration decl, final Map<Integer, List<PropertyDeclaration>> errors) {
		if(null != decl.getConstraint()){
			if((Datatype.RESOURCE.equals(decl.getDatatype()) && decl.getConstraint().isLiteral())
					|| (!Datatype.RESOURCE.equals(decl.getDatatype()) && !decl.getConstraint().isLiteral())){
				appendError(errors, ErrorCodes.SCHEMA_CONSTRAINT_EXCEPTION, decl);
			}
		}
	}

	/**
	 * @param errors
	 * @param errorCode
	 * @param declaration
	 */
	private void appendError(final Map<Integer, List<PropertyDeclaration>> errors, final int errorCode, final PropertyDeclaration declaration) {
		if(errors.containsKey(errorCode)){
			errors.get(errorCode).add(declaration);
		}else{
			List<PropertyDeclaration> fields = new ArrayList<PropertyDeclaration>();
			fields.add(declaration);
			errors.put(errorCode, fields);
		}
	}

}
