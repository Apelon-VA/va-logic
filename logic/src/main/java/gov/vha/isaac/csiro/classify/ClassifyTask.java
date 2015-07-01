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
package gov.vha.isaac.csiro.classify;

import au.csiro.ontology.Ontology;
import gov.vha.isaac.ochre.api.Get;
import gov.vha.isaac.ochre.api.LookupService;
import gov.vha.isaac.ochre.api.chronicle.LatestVersion;
import gov.vha.isaac.ochre.api.classifier.ClassifierResults;
import gov.vha.isaac.ochre.api.component.sememe.SememeSnapshotService;
import gov.vha.isaac.ochre.api.coordinate.EditCoordinate;
import gov.vha.isaac.ochre.api.coordinate.LogicCoordinate;
import gov.vha.isaac.ochre.api.coordinate.StampCoordinate;
import gov.vha.isaac.ochre.api.coordinate.StampPosition;
import gov.vha.isaac.ochre.api.memory.MemoryConfigurations;
import gov.vha.isaac.ochre.api.memory.MemoryManagementService;
import gov.vha.isaac.ochre.collections.ConceptSequenceSet;
import gov.vha.isaac.ochre.collections.NidSet;
import gov.vha.isaac.ochre.collections.SememeSequenceSet;
import gov.vha.isaac.ochre.model.coordinate.StampPositionImpl;
import gov.vha.isaac.ochre.model.sememe.version.LogicGraphSememeImpl;
import gov.vha.isaac.ochre.util.WorkExecutors;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ihtsdo.otf.lookup.contracts.contracts.ActiveTaskSet;

/**
 *
 * @author kec
 */
public class ClassifyTask extends Task<ClassifierResults> {

    private static final Logger log = LogManager.getLogger();

    ClassifierProvider classifierProvider;
    StampCoordinate stampCoordinate;
    LogicCoordinate logicCoordinate;
    EditCoordinate editCoordinate;

    private ClassifyTask(ClassifierProvider classifierProvider) {
        this.classifierProvider = classifierProvider;
        this.stampCoordinate = classifierProvider.stampCoordinate;
        this.logicCoordinate = classifierProvider.logicCoordinate;
        this.editCoordinate = classifierProvider.editCoordinate;
        updateProgress(-1, Long.MAX_VALUE); // Indeterminate progress
        updateTitle("Classification");

    }

    public static ClassifyTask create(ClassifierProvider classifierProvider) {
        ClassifyTask classifyTask = new ClassifyTask(classifierProvider);
        LookupService.getService(ActiveTaskSet.class).get().add(classifyTask);
        LookupService.getService(WorkExecutors.class).getForkJoinPoolExecutor().execute(classifyTask);
        return classifyTask;
    }

    @Override
    protected ClassifierResults call() throws Exception {
        try {
            ClassifierResults results;
            if (classifierProvider.incrementalAllowed()) {
                updateMessage("Incremental classification started...");
                results = incrementalClassification();
            } else {
                updateMessage("Full classification started...");
                results = fullClassification();
            }
            updateValue(results);
            classifierProvider.classifyComplete();
            return results;
        } finally {
            updateProgress(1, 1);
            LookupService.getService(ActiveTaskSet.class).get().remove(this);
        }
    }

    public ClassifierResults incrementalClassification() {
        assert logicCoordinate.getClassifierSequence()
                == editCoordinate.getAuthorSequence() :
                "classifier sequence: " + logicCoordinate.getClassifierSequence()
                + " author sequence: " + editCoordinate.getAuthorSequence();
        updateMessage("Start incremental test.");
        updateMessage("Start to make graph for classification.");
        updateMessage("Start classify.");
        LookupService.getService(MemoryManagementService.class).setMemoryConfiguration(MemoryConfigurations.CLASSIFY);

        Instant incrementalStart = Instant.now();
        NidSet conceptNidSetToClassify = NidSet.of(classifierProvider.getNewConcepts());
        updateMessage("Concepts to classify: " + conceptNidSetToClassify.size());

        AtomicInteger logicGraphMembers = new AtomicInteger();
        AtomicInteger rejectedLogicGraphMembers = new AtomicInteger();
        ClassifierData cd = ClassifierData.get(stampCoordinate, logicCoordinate);
        if (cd.getLastClassifyInstant() != null) {
            updateMessage("Incremental classification ok.");
            StampPosition lastClassifyPosition = new StampPositionImpl(
                    cd.getLastClassifyInstant().toEpochMilli(),
                    editCoordinate.getPathSequence());
            SememeSequenceSet modifiedSememeSequences = Get.sememeService().
                    getSememeSequencesForComponentsFromAssemblageModifiedAfterPosition(
                            conceptNidSetToClassify,
                            logicCoordinate.getStatedAssemblageSequence(),
                            lastClassifyPosition);
            updateMessage("Modified graph count: " + modifiedSememeSequences.size());
            if (modifiedSememeSequences.isEmpty()) {
                updateMessage("No changes to classify.");
            } else {
                ConceptSequenceSet modifiedConcepts
                        = Get.identifierService().getConceptSequencesForReferencedComponents(modifiedSememeSequences);
                updateMessage("Modified concept count: " + modifiedConcepts.size());

                processIncrementalStatedAxioms(stampCoordinate, logicCoordinate,
                        NidSet.of(modifiedConcepts), cd,
                        logicGraphMembers,
                        rejectedLogicGraphMembers);
                updateMessage("classifying new axioms.");
                cd.incrementalClassify();
            }

        } else {
            updateMessage("Full classification required.");
            processAllStatedAxioms(stampCoordinate, logicCoordinate,
                    cd, logicGraphMembers);
            updateMessage("classifying all axioms.");
            cd.classify();
        }
        updateMessage("     classifier data after: " + cd);

        updateMessage("getting results.");
        Ontology res = cd.getClassifiedOntology();
        classifierProvider.getNewConcepts().stream().forEach((sequence) -> {
            au.csiro.ontology.Node incrementalNode = res.getNode(Integer.toString(sequence));

            log.info("Incremental concept: " + sequence);
            log.info("  Parents: " + incrementalNode.getParents());
            log.info("  Equivalent concepts: " + incrementalNode.getEquivalentConcepts());
            log.info("  Child concepts: " + incrementalNode.getChildren());
        });
        ClassifierResults classifierResults = collectResults(res, res.getAffectedNodes());
        classifierProvider.classifyComplete();
        // TODO write back. 
        Instant incrementalEnd = Instant.now();
        Duration incrementalClassifyDuration = Duration.between(incrementalStart, incrementalEnd);
        updateMessage("  Incremental classify duration: " + incrementalClassifyDuration);
        return classifierResults;
    }

    public ClassifierResults fullClassification() {
        assert logicCoordinate.getClassifierSequence()
                == editCoordinate.getAuthorSequence() :
                "classifier sequence: " + logicCoordinate.getClassifierSequence()
                + " author sequence: " + editCoordinate.getAuthorSequence();
        updateMessage("Start classify.");
        AtomicInteger logicGraphMembers = new AtomicInteger();
        Instant classifyStart = Instant.now();
        updateMessage("Start axiom construction.");
        Instant axiomConstructionStart = Instant.now();
        ClassifierData cd = ClassifierData.get(stampCoordinate, logicCoordinate);
        processAllStatedAxioms(stampCoordinate, logicCoordinate,
                cd, logicGraphMembers);
        Instant axiomConstructionEnd = Instant.now();
        Duration axiomConstructionDuration = Duration.between(axiomConstructionStart, axiomConstructionEnd);
        updateMessage("Finished axiom construction. LogicGraphMembers: " + logicGraphMembers);
        updateMessage("Axiom construction duration: " + axiomConstructionDuration);
        updateMessage("Start axiom load.");
        Instant axiomLoadStart = Instant.now();
        LookupService.getService(MemoryManagementService.class).setMemoryConfiguration(MemoryConfigurations.CLASSIFY);
        cd.loadAxioms();
        Instant axiomLoadEnd = Instant.now();
        Duration axiomLoadDuration = Duration.between(axiomLoadStart, axiomLoadEnd);
        updateMessage("Finished axiom load. ");
        updateMessage("Axiom load duration: " + axiomLoadDuration);
        updateMessage("Start reasoner classify. ");
        Instant reasonerClassifyStart = Instant.now();
        cd.classify();
        Instant reasonerClassifyEnd = Instant.now();
        Duration reasonerClassifyDuration = Duration.between(reasonerClassifyStart, reasonerClassifyEnd);
        updateMessage("Finished reasoner classify. ");
        updateMessage("Reasoner classify duration: " + reasonerClassifyDuration);
        Instant retrieveResultsStart = Instant.now();
        Ontology res = cd.getClassifiedOntology();
        Instant retrieveResultsEnd = Instant.now();
        Duration retrieveResultsDuration = Duration.between(retrieveResultsStart, retrieveResultsEnd);
        updateMessage("Finished retrieve results. ");
        updateMessage("Retrieve results duration: " + retrieveResultsDuration);
        ClassifierResults classifierResults = collectResults(res, res.getNodeMap().values());
        Instant classifyEnd = Instant.now();
        Duration classifyDuration = Duration.between(classifyStart, classifyEnd);
        updateMessage("Finished classify. LogicGraphMembers: " + logicGraphMembers);
        updateMessage("Classify duration: " + classifyDuration);
        classifierProvider.classifyComplete();

        return classifierResults;
    }
    
    
    private ClassifierResults collectResults(Ontology res, Collection<au.csiro.ontology.Node> affectedNodes) {
        ConceptSequenceSet affectedConcepts = new ConceptSequenceSet();
        HashSet<ConceptSequenceSet> equivalentSets = new HashSet<>();
        affectedNodes.forEach((node) -> {
            Set<String> equivalentConcepts = node.getEquivalentConcepts();
            if (node.getEquivalentConcepts().size() > 1) {
                ConceptSequenceSet equivalentSet = new ConceptSequenceSet();
                equivalentSets.add(equivalentSet);
                equivalentConcepts.forEach((conceptSequence) -> {
                    equivalentSet.add(Integer.parseInt(conceptSequence));
                    affectedConcepts.add(Integer.parseInt(conceptSequence));
                });
            } else {
                equivalentConcepts.forEach((conceptSequence) -> {
                    try {
                        affectedConcepts.add(Integer.parseInt(conceptSequence));

                    } catch (NumberFormatException numberFormatException) {
                        if (conceptSequence.equals("_BOTTOM_")
                                || conceptSequence.equals("_TOP_")) {
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


    protected void processAllStatedAxioms(StampCoordinate stampCoordinate, LogicCoordinate logicCoordinate, ClassifierData cd, AtomicInteger logicGraphMembers) {
        SememeSnapshotService<LogicGraphSememeImpl> sememeSnapshot = Get.sememeService().getSnapshot(LogicGraphSememeImpl.class, stampCoordinate);
        sememeSnapshot.getLatestSememeVersionsFromAssemblage(logicCoordinate.getStatedAssemblageSequence()).forEach(
                (LatestVersion<LogicGraphSememeImpl> latest) -> {
                    LogicGraphSememeImpl lgs = latest.value();
                    int conceptSequence = Get.identifierService().getConceptSequence(lgs.getReferencedComponentNid());
                    if (Get.conceptService().isConceptActive(conceptSequence, stampCoordinate)) {
                        cd.translate(lgs);
                        logicGraphMembers.incrementAndGet();
                    }
                });
    }

    protected void processIncrementalStatedAxioms(StampCoordinate stampCoordinate,
            LogicCoordinate logicCoordinate, NidSet conceptNidSetToClassify,
            ClassifierData cd, AtomicInteger logicGraphMembers,
            AtomicInteger rejectedLogicGraphMembers) {

        SememeSnapshotService<LogicGraphSememeImpl> sememeSnapshot = Get.sememeService().getSnapshot(LogicGraphSememeImpl.class, stampCoordinate);
        conceptNidSetToClassify.stream().forEach((conceptNid) -> {
            sememeSnapshot.getLatestSememeVersionsForComponentFromAssemblage(conceptNid,
                    logicCoordinate.getStatedAssemblageSequence()).forEach((LatestVersion<LogicGraphSememeImpl> latest) -> {
                        LogicGraphSememeImpl lgs = latest.value();
                        if (conceptNidSetToClassify.contains(lgs.getReferencedComponentNid())) {
                            cd.translateForIncremental(lgs);
                            logicGraphMembers.incrementAndGet();
                        } else {
                            rejectedLogicGraphMembers.incrementAndGet();
                        }
                    });
        });
    }
    
}
