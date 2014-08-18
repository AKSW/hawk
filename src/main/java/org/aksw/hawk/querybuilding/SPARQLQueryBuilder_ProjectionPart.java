package org.aksw.hawk.querybuilding;

import java.util.List;
import java.util.Set;

import org.aksw.autosparql.commons.qald.Question;
import org.aksw.hawk.nlp.MutableTreeNode;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.hp.hpl.jena.rdf.model.impl.ResourceImpl;

public class SPARQLQueryBuilder_ProjectionPart {

	Set<StringBuilder> buildProjectionPart(SPARQLQueryBuilder sparqlQueryBuilder, Question q) {
		Set<StringBuilder> queries = Sets.newHashSet();
		List<MutableTreeNode> bottomUp = getProjectionPathBottumUp(q);

		// empty restriction for projection part in order to account for misinformation in left tree
		// TODO this leads to tremendous increase of runtime
		// queries.add(new StringBuilder("?proj ?p ?o."));
		for (int i = 0; i < bottomUp.size(); ++i) {
			MutableTreeNode bottom = bottomUp.get(i);
			String bottomposTag = bottom.posTag;
			MutableTreeNode top = bottom.parent;
			String topPosTag = bottom.parent.posTag;
			// head of this node is root element
			if (top.parent == null) {
				if (bottomposTag.matches("WRB|WP|NN(.)*")) {
					if (queries.isEmpty()) {
						// is either from Where or Who
						if (bottom.getAnnotations().size() > 0) {
							for (ResourceImpl annotation : bottom.getAnnotations()) {
								queries.add(new StringBuilder("?proj a <" + annotation + ">."));
								queries.add(new StringBuilder("?proj a ?type."));
								//TODO cities like http://dbpedia.org/page/Kirzhach are not annotated as db-owl:Place
								// TODO add super class,e.g., City -> Settlement
							}
						} else {
							sparqlQueryBuilder.log.error("Too less annotations for projection part of the tree!", q.languageToQuestion.get("en"));
						}
					} else {
						// is either from Where or Who
						if (bottom.getAnnotations().size() > 0) {
							for (ResourceImpl annotation : bottom.getAnnotations()) {
								for (StringBuilder existingQueries : queries) {
									existingQueries.append("?proj a <" + annotation + ">.");
									// TODO add super class,e.g., City -> Settlement
								}
							}
						} else {
							sparqlQueryBuilder.log.error("Too less annotations for projection part of the tree!", q.languageToQuestion.get("en"));
						}
					}
				} else if (bottomposTag.equals("CombinedNN")) {
					if (queries.isEmpty()) {
						// combined nouns are lists of abstracts containing does words, i.e., type constraints
						if (bottom.getAnnotations().size() > 0) {
							StringBuilder queryString = new StringBuilder("?proj ?p ?o. FILTER (?proj IN (");
							joinURIsForFilterExpression(bottom, queryString);
							queries.add(queryString);
						} else {
							sparqlQueryBuilder.log.error("Too less annotations for projection part of the tree!", q.languageToQuestion.get("en"));
						}
					} else {
						// combined nouns are lists of abstracts containing does words, i.e., type constraints
						if (bottom.getAnnotations().size() > 0) {
							for (StringBuilder existingQueries : queries) {
								existingQueries.append("?proj ?p ?o. FILTER (?proj IN ( ");
								joinURIsForFilterExpression(bottom, existingQueries);
							}
						} else {
							sparqlQueryBuilder.log.error("Too less annotations for projection part of the tree!", q.languageToQuestion.get("en"));
						}
					}
				} else {
					// strange case since entities should not be the question word type
					sparqlQueryBuilder.log.error("Strange case that never should happen: " + bottomposTag);
				}

			} else {
				// TODO build it in a way, that says that down here are only projection variable constraining modules that need to be advanced by the top heuristically say that here NNs or VBs stand
				// for a predicates
				if (bottomposTag.equals("CombinedNN") && topPosTag.matches("VB(.)*|NN(.)*")) {
					for (ResourceImpl predicates : top.getAnnotations()) {
						if (bottom.getAnnotations().size() > 0) {
							StringBuilder queryString = new StringBuilder("?proj <" + predicates + "> ?o. FILTER (?proj IN (");
							joinURIsForFilterExpression(bottom, queryString);
							queries.add(queryString);

							queryString = new StringBuilder("?o <" + predicates + "> ?proj.FILTER (?proj IN (");
							joinURIsForFilterExpression(bottom, queryString);
							queries.add(queryString);
						}
					}
					i++;
				} else if (bottomposTag.matches("ADD|NN") && topPosTag.matches("VB(.)*|NN(.)*|CombinedNN")) {
					// either way it is an unprecise verb binding
					if (!topPosTag.matches("CombinedNN")) {
						for (ResourceImpl annotation : top.getAnnotations()) {
							StringBuilder queryString = new StringBuilder("?proj <" + annotation + "> <" + bottom.label + ">.");
							queries.add(queryString);
						}
					}
					// or it stems from a full-text look up (+ reversing of the predicates)
					if (top.getAnnotations().size() > 0) {
						StringBuilder queryString = new StringBuilder("?proj ?p <" + bottom.label + ">. FILTER (?proj IN ( ");
						joinURIsForFilterExpression(top, queryString);
						queries.add(queryString);

						queryString = new StringBuilder("<" + bottom.label + "> ?p ?proj. FILTER (?proj IN ( ");
						joinURIsForFilterExpression(top, queryString);
						queries.add(queryString);
					}
					i++;
				} else {
					sparqlQueryBuilder.log.error("Strange case that never should happen: " + bottomposTag);
				}
			}
		}
		return queries;
	}

	/**
	 * 
	 * @param top
	 *            contains |URIs|=n
	 * @param queryString
	 *            contains so far a SOMETHING. FILTER (?proj IN (...)). Goal is to insert the URIs into the brackets in a valid SPARQL way
	 */
	private void joinURIsForFilterExpression(MutableTreeNode top, StringBuilder queryString) {
		for (ResourceImpl annotation : top.getAnnotations()) {
			queryString.append("<" + annotation.getURI() + "> , ");
		}
		queryString.deleteCharAt(queryString.lastIndexOf(",")).append(")).");
	}

	private List<MutableTreeNode> getProjectionPathBottumUp(Question q) {
		List<MutableTreeNode> bottomUp = Lists.newArrayList();
		// iterate through left tree part
		// assumption: this part of the tree is a path
		MutableTreeNode tmp = q.tree.getRoot().getChildren().get(0);
		while (tmp != null) {
			bottomUp.add(tmp);
			if (!tmp.getChildren().isEmpty()) {
				tmp = tmp.getChildren().get(0);
			} else {
				tmp = null;
			}
		}
		bottomUp = Lists.reverse(bottomUp);
		return bottomUp;
	}
}