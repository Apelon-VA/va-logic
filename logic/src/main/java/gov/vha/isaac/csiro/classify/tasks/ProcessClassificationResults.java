/*
 * Copyright 2015 U.S. Department of Veterans Affairs.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gov.vha.isaac.csiro.classify.tasks;

import au.csiro.ontology.Node;
import au.csiro.ontology.Ontology;
import gov.vha.isaac.csiro.classify.ClassifierData;
import gov.vha.isaac.ochre.api.classifier.ClassifierResults;
import gov.vha.isaac.ochre.api.coordinate.LogicCoordinate;
import gov.vha.isaac.ochre.api.coordinate.StampCoordinate;
import gov.vha.isaac.ochre.api.task.TimedTask;
import gov.vha.isaac.ochre.collections.ConceptSequenceSet;
import java.util.HashSet;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author kec
 */
public class ProcessClassificationResults extends TimedTask<ClassifierResults> {
    private static final Logger log = LogManager.getLogger();

    StampCoordinate stampCoordinate;
    LogicCoordinate logicCoordinate;

    public ProcessClassificationResults(StampCoordinate stampCoordinate,
                         LogicCoordinate logicCoordinate) {
        this.stampCoordinate = stampCoordinate;
        this.logicCoordinate = logicCoordinate;
       updateTitle("Retrieve inferred axioms");
    }

    @Override
    protected ClassifierResults call() throws Exception {
        ClassifierData cd = ClassifierData.get(stampCoordinate, logicCoordinate);
        Ontology res = cd.getClassifiedOntology();
        ClassifierResults classifierResults = collectResults(res, cd.getAffectedConceptSequenceSet());
        return classifierResults;
    }
    
    private ClassifierResults collectResults(Ontology res, ConceptSequenceSet affectedConcepts) {
        HashSet<ConceptSequenceSet> equivalentSets = new HashSet<>();
        affectedConcepts.parallelStream().forEach((conceptSequence) -> {
            Node node = res.getNode(Integer.toString(conceptSequence));
            if (node == null) {
                throw new RuntimeException("Null node for: " + conceptSequence);
            }
            Set<String> equivalentConcepts = node.getEquivalentConcepts();
            if (node.getEquivalentConcepts().size() > 1) {
                ConceptSequenceSet equivalentSet = new ConceptSequenceSet();
                equivalentSets.add(equivalentSet);
                equivalentConcepts.forEach((equivalentConceptSequence) -> {
                    equivalentSet.add(Integer.parseInt(equivalentConceptSequence));
                    affectedConcepts.add(Integer.parseInt(equivalentConceptSequence));
                });
            } else {
                equivalentConcepts.forEach((equivalentConceptSequence) -> {
                    try {
                        affectedConcepts.add(Integer.parseInt(equivalentConceptSequence));

                    } catch (NumberFormatException numberFormatException) {
                        if (equivalentConceptSequence.equals("_BOTTOM_")
                                || equivalentConceptSequence.equals("_TOP_")) {
                            // do nothing. 
                        } else {
                            throw numberFormatException;
                        }
                    }
                });
            }
        });
        return new ClassifierResults(affectedConcepts, equivalentSets);
    }
    
}
