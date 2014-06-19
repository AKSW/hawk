package org.aksw.hawk.controller;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import org.aksw.autosparql.commons.qald.QALD4_EvaluationUtils;
import org.aksw.autosparql.commons.qald.QALD_Loader;
import org.aksw.autosparql.commons.qald.Question;
import org.aksw.hawk.index.IndexDBO_classes;
import org.aksw.hawk.index.IndexDBO_properties;
import org.aksw.hawk.module.Fulltexter;
import org.aksw.hawk.module.ModuleBuilder;
import org.aksw.hawk.module.PseudoQueryBuilder;
import org.aksw.hawk.module.SystemAnswerer;
import org.aksw.hawk.nlp.Pruner;
import org.aksw.hawk.nlp.SentenceToSequence;
import org.aksw.hawk.nlp.posTree.MutableTree;
import org.aksw.hawk.nlp.posTree.MutableTreeNode;
import org.aksw.hawk.nlp.spotter.ASpotter;
import org.aksw.hawk.nlp.util.CachedParseTree;
import org.aksw.hawk.pruner.GraphNonSCCPruner;
import org.aksw.hawk.pruner.QueryVariableHomomorphPruner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.hp.hpl.jena.rdf.model.RDFNode;

public class PipelineShortRecall {
	static Logger log = LoggerFactory.getLogger(PipelineShortRecall.class);
	String dataset;
	QALD_Loader datasetLoader;
	ASpotter nerdModule;
	CachedParseTree cParseTree;
	ModuleBuilder moduleBuilder;
	PseudoQueryBuilder pseudoQueryBuilder;
	Pruner pruner;
	SystemAnswerer systemAnswerer;
	QueryVariableHomomorphPruner queryVariableHomomorphPruner;
	GraphNonSCCPruner graphNonSCCPruner;
	Visualizer vis = new Visualizer();
	SentenceToSequence sentenceToSequence;
	Fulltexter fulltexter;

	void run() throws IOException {
		// 1. read in Questions from QALD 4
		List<Question> questions = datasetLoader.load(dataset);
		double overallf = 0;
		double overallp = 0;
		double overallr = 0;
		double counter = 0;

		for (Question q : questions) {
			// by now only work on resource questions
			if (q.answerType.equals("resource") && isSELECTquery(q.pseudoSparqlQuery, q.sparqlQuery)) {
				log.info("->" + q.languageToQuestion);
				// 2. Disambiguate parts of the query
				q.languageToNamedEntites = nerdModule.getEntities(q.languageToQuestion.get("en"));

				// 3. Build trees from questions and cache them
				q.tree = cParseTree.process(q);
				// noun combiner, decrease #nodes in the DEPTree
				// decreases
				sentenceToSequence.combineSequences(q);
				// 4. Apply pruning rules
				q.tree = pruner.prune(q);

//				log.info(q.tree.toString());

				annotateTree(q.tree);

				HashMap<String, Set<RDFNode>> answer = fulltexter.fulltext(q);
				for (String key : answer.keySet()) {
					Set<RDFNode> systemAnswers = answer.get(key);
					// 11. Compare to set of resources from benchmark
					double precision = QALD4_EvaluationUtils.precision(systemAnswers, q);
					double recall = QALD4_EvaluationUtils.recall(systemAnswers, q);
					double fMeasure = QALD4_EvaluationUtils.fMeasure(systemAnswers, q);
					if (fMeasure > 0) {
//						log.info("\tP=" + precision + " R=" + recall + " F=" + fMeasure);
						overallf += fMeasure;
						overallp += precision;
						overallr += recall;
						counter++;
					}
				}
				// break;
			}
		}
		log.info("Average P=" + overallp / counter + " R=" + overallr / counter + " F=" + overallf / counter);
	}

	private void annotateTree(MutableTree tree) {
		IndexDBO_classes classesIndex = new IndexDBO_classes();
		IndexDBO_properties propertiesIndex = new IndexDBO_properties();
		Stack<MutableTreeNode> stack = new Stack<>();
		stack.push(tree.getRoot());
		while (!stack.isEmpty()) {
			MutableTreeNode tmp = stack.pop();
			String label = tmp.label;
			String posTag = tmp.posTag;
			// for each VB* ask property index
			if (posTag.matches("VB(.)*")) {
				System.out.println(label + " \t\t\t= " + Joiner.on(", ").join(propertiesIndex.search(label)));
			}
			// for each NN* ask class index
			else if (posTag.matches("NN(.)*")) {
				System.out.println(label + " \t\t\t= " + Joiner.on(", ").join(classesIndex.search(label)));
			}
			for (MutableTreeNode child : tmp.getChildren()) {
				stack.push(child);
			}
		}
	}

	private boolean isSELECTquery(String pseudoSparqlQuery, String sparqlQuery) {
		if (pseudoSparqlQuery != null) {
			return pseudoSparqlQuery.contains("\nSELECT\n") || pseudoSparqlQuery.contains("SELECT ");
		} else if (sparqlQuery != null) {
			return sparqlQuery.contains("\nSELECT\n") || sparqlQuery.contains("SELECT ");
		}
		return false;
	}

}