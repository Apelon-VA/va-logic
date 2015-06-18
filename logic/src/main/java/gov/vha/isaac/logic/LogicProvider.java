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
package gov.vha.isaac.logic;

import gov.vha.isaac.ochre.api.logic.LogicService;
import gov.vha.isaac.csiro.classify.ClassifierProvider;
import gov.vha.isaac.ochre.api.classifier.ClassifierService;
import gov.vha.isaac.ochre.api.coordinate.EditCoordinate;
import gov.vha.isaac.ochre.api.coordinate.LogicCoordinate;
import gov.vha.isaac.ochre.api.coordinate.StampCoordinate;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.hk2.runlevel.RunLevel;
import org.jvnet.hk2.annotations.Service;

/**
 *
 * @author kec
 */
@Service(name = "logic provider")
@RunLevel(value = 2)
public class LogicProvider implements LogicService {
    private static final Logger log = LogManager.getLogger();
    
    private static final Map<ClassifierServiceKey, ClassifierService> classifierServiceMap = new ConcurrentHashMap<>();

    private LogicProvider() {
        //For HK2
        log.info("logic provider constructed");
    }

    @PostConstruct
    private void startMe() throws IOException {
        log.info("Starting LogicProvider.");
    }

    @PreDestroy
    private void stopMe() throws IOException {
        log.info("Stopping LogicProvider.");
    }

    @Override
    public ClassifierService getClassifierService(
            StampCoordinate stampCoordinate, 
            LogicCoordinate logicCoordinate, 
            EditCoordinate editCoordinate) {
        ClassifierServiceKey key = new ClassifierServiceKey(stampCoordinate, logicCoordinate, editCoordinate);
        if (!classifierServiceMap.containsKey(key)) {
            classifierServiceMap.putIfAbsent(key, 
                    new ClassifierProvider(stampCoordinate, logicCoordinate, editCoordinate));
        }
        return classifierServiceMap.get(key);
    }
    
    private static class ClassifierServiceKey {
        StampCoordinate stampCoordinate;
        LogicCoordinate logicCoordinate;
        EditCoordinate editCoordinate;

        public ClassifierServiceKey(StampCoordinate stampCoordinate, LogicCoordinate logicCoordinate, EditCoordinate editCoordinate) {
            this.stampCoordinate = stampCoordinate;
            this.logicCoordinate = logicCoordinate;
            this.editCoordinate = editCoordinate;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 59 * hash + Objects.hashCode(this.logicCoordinate);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ClassifierServiceKey other = (ClassifierServiceKey) obj;
            if (!Objects.equals(this.stampCoordinate, other.stampCoordinate)) {
                return false;
            }
            if (!Objects.equals(this.logicCoordinate, other.logicCoordinate)) {
                return false;
            }
            if (!Objects.equals(this.editCoordinate, other.editCoordinate)) {
                return false;
            }
            return true;
        }
    }
}
