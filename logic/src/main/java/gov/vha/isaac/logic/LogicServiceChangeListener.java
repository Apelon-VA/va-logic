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
package gov.vha.isaac.logic;

import gov.vha.isaac.ochre.api.chronicle.ChronicledConcept;
import gov.vha.isaac.ochre.api.commit.ChangeListener;
import gov.vha.isaac.ochre.api.coordinate.LogicCoordinate;
import gov.vha.isaac.ochre.api.sememe.SememeChronicle;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author kec
 */
public class LogicServiceChangeListener implements ChangeListener {
    private static final Logger log = LogManager.getLogger();
    
    private final UUID listenerUuid = UUID.randomUUID();
    private final LogicCoordinate logicCoordinate;
    private final LogicProvider logicProvider;
    

    public LogicServiceChangeListener(LogicCoordinate logicCoordinate, LogicProvider logicProvider) {
        this.logicCoordinate = logicCoordinate;
        this.logicProvider = logicProvider;
    }
    
    @Override
    public UUID getListenerUuid() {
        return listenerUuid;
    }

    @Override
    public void handleChange(ChronicledConcept cc) {
        // Nothing to do...
    }

    @Override
    public void handleChange(SememeChronicle sc) {
        if (sc.getAssemblageSequence() == logicCoordinate.getStatedAssemblageSequence()) {
            log.info("Stated form change: " + sc);
        } else if (sc.getAssemblageSequence() == logicCoordinate.getInferredAssemblageSequence()) {
            log.info("Inferred form change: " + sc);
        }
    }
    
}
