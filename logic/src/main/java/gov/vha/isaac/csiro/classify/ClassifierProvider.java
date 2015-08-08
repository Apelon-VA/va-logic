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

import gov.vha.isaac.cradle.taxonomy.CradleTaxonomyProvider;
import gov.vha.isaac.cradle.taxonomy.graph.GraphCollector;
import gov.vha.isaac.csiro.classify.tasks.AggregateClassifyTask;
import gov.vha.isaac.metadata.coordinates.ViewCoordinates;
import gov.vha.isaac.ochre.api.Get;
import gov.vha.isaac.ochre.api.classifier.ClassifierResults;
import gov.vha.isaac.ochre.api.classifier.ClassifierService;
import gov.vha.isaac.ochre.api.coordinate.EditCoordinate;
import gov.vha.isaac.ochre.api.coordinate.LogicCoordinate;
import gov.vha.isaac.ochre.api.coordinate.StampCoordinate;
import gov.vha.isaac.ochre.api.logic.LogicalExpression;
import gov.vha.isaac.ochre.api.tree.hashtree.HashTreeBuilder;
import gov.vha.isaac.ochre.api.tree.hashtree.HashTreeWithBitSets;
import java.io.IOException;
import java.util.stream.IntStream;
import javafx.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author kec
 */
public class ClassifierProvider implements ClassifierService {

    private static final Logger log = LogManager.getLogger();

    StampCoordinate stampCoordinate;
    LogicCoordinate logicCoordinate;
    EditCoordinate editCoordinate;

    public ClassifierProvider(StampCoordinate stampCoordinate,
            LogicCoordinate logicCoordinate,
            EditCoordinate editCoordinate) {
        this.stampCoordinate = stampCoordinate;
        this.logicCoordinate = logicCoordinate;
        this.editCoordinate = editCoordinate;
    }

    @Override
    public Task<ClassifierResults> classify() {
        return AggregateClassifyTask.get(this.stampCoordinate, this.logicCoordinate);
    }

    protected HashTreeWithBitSets getStatedTaxonomyGraph() {
        try {
            IntStream conceptSequenceStream = Get.identifierService().getParallelConceptSequenceStream();
            GraphCollector collector = new GraphCollector(((CradleTaxonomyProvider) Get.taxonomyService()).getOriginDestinationTaxonomyRecords(),
                    ViewCoordinates.getDevelopmentStatedLatestActiveOnly());
            HashTreeBuilder graphBuilder = conceptSequenceStream.collect(
                    HashTreeBuilder::new,
                    collector,
                    collector);
            HashTreeWithBitSets resultGraph = graphBuilder.getSimpleDirectedGraphGraph();
            return resultGraph;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    protected HashTreeWithBitSets getInferredTaxonomyGraph() {
        IntStream conceptSequenceStream = Get.identifierService().getParallelConceptSequenceStream();
        GraphCollector collector = new GraphCollector(((CradleTaxonomyProvider) Get.taxonomyService()).getOriginDestinationTaxonomyRecords(),
                ViewCoordinates.getDevelopmentInferredLatestActiveOnly());
        HashTreeBuilder graphBuilder = conceptSequenceStream.collect(
                HashTreeBuilder::new,
                collector,
                collector);
        HashTreeWithBitSets resultGraph = graphBuilder.getSimpleDirectedGraphGraph();
        return resultGraph;
    }

    @Override
    public Task<Integer> getConceptSequenceForExpression(LogicalExpression expression,
            EditCoordinate editCoordinate) {
        return GetConceptSequenceForExpressionTask.create(expression, this, editCoordinate);
    }

}
