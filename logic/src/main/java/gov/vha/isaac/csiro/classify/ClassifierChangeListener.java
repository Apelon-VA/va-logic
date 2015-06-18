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

import gov.vha.isaac.ochre.api.IdentifierService;
import gov.vha.isaac.ochre.api.LookupService;
import gov.vha.isaac.ochre.api.component.concept.ConceptChronology;
import gov.vha.isaac.ochre.api.commit.ChronologyChangeListener;
import gov.vha.isaac.ochre.api.coordinate.LogicCoordinate;
import gov.vha.isaac.ochre.api.component.sememe.SememeChronology;
import gov.vha.isaac.ochre.collections.ConceptSequenceSet;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author kec
 */
public class ClassifierChangeListener implements ChronologyChangeListener {
    private static final Logger log = LogManager.getLogger();
    private static IdentifierService idService;
    protected static IdentifierService getIdentifierService() {
        if (idService == null) {
            idService = LookupService.getService(IdentifierService.class);
        }
        return idService;
    }
    
    private final UUID listenerUuid = UUID.randomUUID();
    private final LogicCoordinate logicCoordinate;
    private final ClassifierProvider classifierProvider;
    private boolean incrementalAllowed = false;
    ConceptSequenceSet newConcepts = new ConceptSequenceSet();

    public ConceptSequenceSet getNewConcepts() {
        return newConcepts;
    }
    

    public ClassifierChangeListener(LogicCoordinate logicCoordinate, ClassifierProvider classifierProvider) {
        this.logicCoordinate = logicCoordinate;
        this.classifierProvider = classifierProvider;
    }
    
    @Override
    public UUID getListenerUuid() {
        return listenerUuid;
    }

    @Override
    public void handleChange(ConceptChronology cc) {
        // Nothing to do...
    }
    
    public boolean incrementalAllowed() {
        return incrementalAllowed;
    }

    public void classifyComplete() {
        newConcepts = new ConceptSequenceSet();
        incrementalAllowed = true;
    }
    @Override
    public void handleChange(SememeChronology sc) {
        if (sc.getAssemblageSequence() == logicCoordinate.getStatedAssemblageSequence()) {
            // get classifier stampCoordinate for last classify. 
            // See if there is a change in the latest vs the last classify. 
            // See if the change has deletions, if so then incremental is not allowed. 
            newConcepts.add(getIdentifierService().getConceptSequence(sc.getReferencedComponentNid()));
            log.info("Stated form change: " + sc);
        } else if (sc.getAssemblageSequence() == logicCoordinate.getInferredAssemblageSequence()) {
            log.info("Inferred form change: " + sc);
        }
    }
    
}
