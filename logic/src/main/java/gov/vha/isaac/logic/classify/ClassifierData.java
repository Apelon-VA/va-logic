/*
 * Copyright 2015 kec.
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
package gov.vha.isaac.logic.classify;

import au.csiro.ontology.Ontology;
import au.csiro.ontology.classification.IReasoner;
import au.csiro.snorocket.core.SnorocketReasoner;
import gov.vha.isaac.logic.axioms.GraphToAxiomTranslator;
import gov.vha.isaac.ochre.api.coordinate.LogicCoordinate;
import gov.vha.isaac.ochre.api.coordinate.StampCoordinate;
import gov.vha.isaac.ochre.model.sememe.version.LogicGraphSememeImpl;
import java.time.Instant;

/**
 *
 * @author kec
 */
public class ClassifierData {
    private static ClassifierData singleton;
    
    GraphToAxiomTranslator graphToAxiomTranslator = new GraphToAxiomTranslator();
    GraphToAxiomTranslator incrementalToAxiomTranslator = new GraphToAxiomTranslator();
    IReasoner reasoner = new SnorocketReasoner();

    Instant lastClassifyInstant;

    StampCoordinate stampCoordinate;
    LogicCoordinate logicCoordinate;

    private ClassifierData(StampCoordinate stampCoordinate, LogicCoordinate logicCoordinate) {
        this.stampCoordinate = stampCoordinate;
        this.logicCoordinate = logicCoordinate;
    }
    
    public static ClassifierData get(StampCoordinate stampCoordinate, LogicCoordinate logicCoordinate) {
        if (singleton == null) {
            singleton = new ClassifierData(stampCoordinate, logicCoordinate);
        } else {
            if (!singleton.stampCoordinate.equals(stampCoordinate) || !singleton.logicCoordinate.equals(logicCoordinate)) {
                singleton = new ClassifierData(stampCoordinate, logicCoordinate);
            } 
        }
        return singleton;
    }

    public void translate(LogicGraphSememeImpl lgs) {
        graphToAxiomTranslator.translate(lgs);
    }
    
    public void translateForIncremental(LogicGraphSememeImpl lgs) {
        incrementalToAxiomTranslator.translate(lgs);
    }
    
    public void loadAxioms() {
        reasoner.loadAxioms(graphToAxiomTranslator.getAxioms());
    }


    public IReasoner classify() {
        lastClassifyInstant = Instant.now();
        return reasoner.classify();
    }

    public IReasoner Incrementallassify() {
        lastClassifyInstant = Instant.now();
        reasoner.loadAxioms(incrementalToAxiomTranslator.getAxioms());
        graphToAxiomTranslator.getAxioms().addAll(incrementalToAxiomTranslator.getAxioms());
        incrementalToAxiomTranslator.getAxioms().clear();
        return reasoner.classify();
    }

    public Ontology getClassifiedOntology() {
        return reasoner.getClassifiedOntology();
    }

    public boolean isClassified() {
        return reasoner.isClassified();
    }  
    
    public Instant getLastClassifyInstant() {
        return this.lastClassifyInstant;
    }    
    
    
}
