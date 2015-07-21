package com.ikanow.aleph2.data_import_manager.batch_enrichment.services.mapreduce;

import java.util.Arrays;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Injector;
import com.google.inject.Scopes;
import com.ikanow.aleph2.data_import_manager.batch_enrichment.actors.BeBucketActor;
import com.ikanow.aleph2.data_import_manager.batch_enrichment.module.DataImportManagerModule;
import com.ikanow.aleph2.data_import_manager.batch_enrichment.services.DataImportManager;
import com.ikanow.aleph2.data_import_manager.batch_enrichment.utils.DataBucketTest;
import com.ikanow.aleph2.data_import_manager.stream_enrichment.services.IStormController;
import com.ikanow.aleph2.data_import_manager.stream_enrichment.services.LocalStormController;
import com.ikanow.aleph2.data_model.utils.ErrorUtils;
import com.ikanow.aleph2.data_model.utils.ModuleUtils;

public class BeJobServiceTest extends DataBucketTest {
    private static final Logger logger = LogManager.getLogger(BeJobServiceTest.class);

	protected IBeJobService beJobService;

	protected static boolean useMiniCluster = false;
	
	@Before
	public void setupDependencies() throws Exception {
		try{
		super.setupDependencies();
		Injector serverInjector = ModuleUtils.createInjector(Arrays.asList(new DataImportManagerModule(){

			@Override
			protected void configureServices() {
			    bind(DataImportManager.class).in(Scopes.SINGLETON);
			    bind(IBeJobService.class).to(useMiniCluster?MiniClusterBeJobLauncher.class:LocalBeJobLauncher.class).in(Scopes.SINGLETON);
			    bind(IStormController.class).to(LocalStormController.class).in(Scopes.SINGLETON);
			}
			
		}), Optional.of(config));

		this.beJobService = serverInjector.getInstance(IBeJobService.class);		
	}
		catch (Throwable t) { 
			System.out.println(ErrorUtils.getLongForm("{0}", t));
			throw t; 
			}
	} // setup dependencies
	
	@Test
	public void testBeJobService() throws Exception{
		try {
			
			createEnhancementBeanInDb();
			
			BeBucketActor.launchReadyJobs(fileContext, buckeFullName1, bucketPath1, beJobService, _management_db, null);
			if(beJobService instanceof MiniClusterBeJobLauncher){
				((MiniClusterBeJobLauncher)beJobService).stop();
			}
		} catch (Throwable t) {
			logger.error("testBeJobService caught exception",t);
			throw t;
		}
	}
}
