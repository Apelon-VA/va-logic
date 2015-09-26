/**
 * Copyright Notice
 *
 * This is a work of the U.S. Government and is not subject to copyright
 * protection in the United States. Foreign copyrights may apply.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gov.vha.isaac.expression.parser;

import gov.vha.isaac.metadata.coordinates.EditCoordinates;
import gov.vha.isaac.metadata.coordinates.LogicCoordinates;
import gov.vha.isaac.metadata.coordinates.StampCoordinates;
import gov.vha.isaac.ochre.api.ConceptModel;
import gov.vha.isaac.ochre.api.ConfigurationService;
import gov.vha.isaac.ochre.api.Get;
import gov.vha.isaac.ochre.api.LookupService;
import gov.vha.isaac.ochre.api.classifier.ClassifierResults;
import gov.vha.isaac.ochre.api.classifier.ClassifierService;
import gov.vha.isaac.ochre.api.constants.Constants;
import gov.vha.isaac.ochre.api.coordinate.EditCoordinate;
import gov.vha.isaac.ochre.api.coordinate.LogicCoordinate;
import gov.vha.isaac.ochre.api.coordinate.StampCoordinate;
import gov.vha.isaac.ochre.api.logic.LogicalExpression;
import gov.vha.isaac.ochre.api.logic.LogicalExpressionBuilder;
import gov.vha.isaac.ochre.api.memory.HeapUseTicker;
import gov.vha.isaac.ochre.api.progress.ActiveTasksTicker;
import gov.vha.isaac.ochre.model.logic.LogicalExpressionOchreImpl;
import java.io.File;
import org.apache.logging.log4j.LogManager;

/**
 * 
 * {@link Main}
 *
 * @author Tony Weida
 */
public class Main {
	
	public static void main(String[] args) {
		if (args == null || args.length == 0) {
			args = new String[] {new File("../../va-isaac-gui-pa/app-assembly/solor-all-1.13-SNAPSHOT-all.data").getAbsolutePath()};
		}
		System.out.println("Build directory: " + args[0]);
		System.setProperty(Constants.DATA_STORE_ROOT_LOCATION_PROPERTY, args[0]);
		LookupService.getService(ConfigurationService.class).setConceptModel(ConceptModel.OCHRE_CONCEPT_MODEL);
		LookupService.startupIsaac();
		HeapUseTicker.start(10);
		ActiveTasksTicker.start(10);

		System.out.println("System up...");
		
		try
		{
			LogicCoordinate logicCoordinate = LogicCoordinates.getStandardElProfile();
			StampCoordinate stampCoordinate = StampCoordinates.getDevelopmentLatest();
			EditCoordinate editCoordinate = EditCoordinates.getDefaultUserSolorOverlay();
			ClassifierService classifierService = Get.logicService().getClassifierService(stampCoordinate, logicCoordinate, editCoordinate);
	
			//alt file
			//new File("../logic-integration-tests/src/test/resources/expressionSample.txt");
			ExpressionReader.read(new File("xder2_sscccRefset_LOINCExpressionAssociationFull_INT_20150801.txt")).forEach(parseTree ->
			{
				try
				{
					LogicalExpressionBuilder defBuilder = Get.logicalExpressionBuilderService().getLogicalExpressionBuilder();
					ISAACVisitor visitor = new ISAACVisitor(defBuilder);
					visitor.visit(parseTree);
					LogicalExpression expression = defBuilder.build();
					System.out.println("LOINC EXPRESSION SERVICE> Created definition:\n\n " + expression);
					
					int newSequence = classifierService.getConceptSequenceForExpression((LogicalExpressionOchreImpl) expression, editCoordinate).get();
					System.out.println("LOINC EXPRESSION SERVICE> New sequence: " + newSequence);
				}
				catch (Exception e)
				{
					LogManager.getLogger().error("Error processing tree " + parseTree.toStringTree(), e);
					throw new RuntimeException(e);
				}
			});
		
			System.out.println("LOINC EXPRESSION SERVICE> Classifying ...");
			ClassifierResults results = classifierService.classify().get();
			System.out.println("LOINC EXPRESSION SERVICE> Classification results: " + results);
	
			System.out.println("LOINC EXPRESSION SERVICE> Done.");

		} 
		catch (Throwable ex) {
			LogManager.getLogger().error(ex);
		}
		HeapUseTicker.stop();
		ActiveTasksTicker.stop();
		LookupService.shutdownIsaac();
		System.out.println("System down...");
		System.exit(0);
	}
}
