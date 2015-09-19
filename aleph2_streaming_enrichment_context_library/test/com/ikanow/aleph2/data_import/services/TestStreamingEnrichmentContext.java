/*******************************************************************************
* Copyright 2015, The IKANOW Open Source Project.
* 
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License, version 3,
* as published by the Free Software Foundation.
* 
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Affero General Public License for more details.
* 
* You should have received a copy of the GNU Affero General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
******************************************************************************/
package com.ikanow.aleph2.data_import.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import scala.Tuple2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Injector;
import com.ikanow.aleph2.data_import.context.stream_enrichment.utils.ErrorUtils;
import com.ikanow.aleph2.data_model.interfaces.data_import.IEnrichmentModuleContext;
import com.ikanow.aleph2.data_model.interfaces.data_services.IManagementDbService;
import com.ikanow.aleph2.data_model.interfaces.data_services.ISearchIndexService;
import com.ikanow.aleph2.data_model.interfaces.data_services.IStorageService;
import com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService;
import com.ikanow.aleph2.data_model.interfaces.shared_services.IDataWriteService;
import com.ikanow.aleph2.data_model.interfaces.shared_services.IUnderlyingService;
import com.ikanow.aleph2.data_model.objects.data_import.AnnotationBean;
import com.ikanow.aleph2.data_model.objects.data_import.DataBucketBean;
import com.ikanow.aleph2.data_model.objects.data_import.DataSchemaBean;
import com.ikanow.aleph2.data_model.objects.shared.AssetStateDirectoryBean;
import com.ikanow.aleph2.data_model.objects.shared.SharedLibraryBean;
import com.ikanow.aleph2.data_model.utils.BeanTemplateUtils;
import com.ikanow.aleph2.data_model.utils.ContextUtils;
import com.ikanow.aleph2.data_model.utils.CrudUtils;
import com.ikanow.aleph2.data_model.utils.ModuleUtils;
import com.ikanow.aleph2.data_model.utils.Tuples;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class TestStreamingEnrichmentContext {
	static final Logger _logger = LogManager.getLogger(); 

	protected ObjectMapper _mapper = BeanTemplateUtils.configureMapper(Optional.empty());
	protected Injector _app_injector;
	
	@Before
	public void injectModules() throws Exception {
		_logger.info("run injectModules");
		
		final Config config = ConfigFactory.parseFile(new File("./example_config_files/context_local_test.properties"));
		
		try {
			_app_injector = ModuleUtils.createTestInjector(Arrays.asList(), Optional.of(config));
		}
		catch (Exception e) {
			try {
				e.printStackTrace();
			}
			catch (Exception ee) {
				System.out.println(ErrorUtils.getLongForm("{0}", e));
			}
		}
	}
	
	@Test
	public void test_basicContextCreation() {
		_logger.info("run test_basicContextCreation");
		try {
			assertTrue("Injector created", _app_injector != null);
		
			final StreamingEnrichmentContext test_context = _app_injector.getInstance(StreamingEnrichmentContext.class);
			
			assertTrue("StreamingEnrichmentContext created", test_context != null);
			
			assertTrue("StreamingEnrichmentContext dependencies", test_context._core_management_db != null);
			assertTrue("StreamingEnrichmentContext dependencies", test_context._distributed_services != null);
			assertTrue("StreamingEnrichmentContext dependencies", test_context._index_service != null);
			assertTrue("StreamingEnrichmentContext dependencies", test_context._globals != null);
			assertTrue("StreamingEnrichmentContext dependencies", test_context._service_context != null);
			
			assertTrue("Find service", test_context.getServiceContext().getService(ISearchIndexService.class, Optional.empty()).isPresent());
			
			// Check if started in "technology" (internal mode)
			assertEquals(test_context._state_name, StreamingEnrichmentContext.State.IN_TECHNOLOGY);
			
			// Check that multiple calls to create harvester result in different contexts but with the same injection:
			final StreamingEnrichmentContext test_context2 = _app_injector.getInstance(StreamingEnrichmentContext.class);
			assertTrue("StreamingEnrichmentContext created", test_context2 != null);
			assertTrue("StreamingEnrichmentContexts different", test_context2 != test_context);
			assertEquals(test_context._service_context, test_context2._service_context);
			
			// Check calls that require that bucket/endpoint be set
			final DataBucketBean test_bucket = BeanTemplateUtils.build(DataBucketBean.class)
					.with(DataBucketBean::_id, "test")
					.with(DataBucketBean::full_name, "/test/basicContextCreation")
					.with(DataBucketBean::modified, new Date())
					.with("data_schema", BeanTemplateUtils.build(DataSchemaBean.class)
							.with("search_index_schema", BeanTemplateUtils.build(DataSchemaBean.SearchIndexSchemaBean.class)
									.done().get())
							.done().get())
					.done().get();
			
			final SharedLibraryBean library = BeanTemplateUtils.build(SharedLibraryBean.class)
					.with(SharedLibraryBean::path_name, "/test/lib")
					.done().get();
			test_context.setLibraryConfig(library);
			
			try {
				test_context.getTopologyStorageEndpoint(null, null);
				fail("Should have errored");
			}
			catch(Exception e) {
				assertEquals(ErrorUtils.get(ErrorUtils.USER_TOPOLOGY_NOT_SET, "getTopologyStorageEndpoint"), e.getMessage());
			}
			test_context.setUserTopologyEntryPoint("test");
			test_context.setBucket(test_bucket);
			assertEquals(test_bucket, test_context.getBucket().get());
			assertTrue("getTopologyStorageEndpoint call succeeded", null != test_context.getTopologyStorageEndpoint(Object.class, Optional.empty()));
			assertTrue("getTopologyStorageEndpoint call succeeded", null != test_context.getTopologyStorageEndpoint(Object.class, Optional.of(test_bucket)));
			//Other topology call
			try {
				test_context2.getTopologyEntryPoints(Object.class, Optional.empty());
				fail("Should have errored");
			}
			catch(Exception e) {
			}
			test_context2.setUserTopologyEntryPoint("test");
			test_context2.setBucket(test_bucket);
			assertTrue("getTopologyEntryPoint call succeeded", null != test_context2.getTopologyEntryPoints(Object.class, Optional.empty()));
			assertTrue("getTopologyEntryPoint call succeeded", null != test_context2.getTopologyEntryPoints(Object.class, Optional.of(test_bucket)));
		}
		catch (Exception e) {
			System.out.println(ErrorUtils.getLongForm("{1}: {0}", e, e.getClass()));
			throw e;
		}
	}
	
	@Test
	public void test_ExternalContextCreation() throws InstantiationException, IllegalAccessException, ClassNotFoundException, InterruptedException, ExecutionException {
		_logger.info("run test_ExternalContextCreation");
		try {
			assertTrue("Config contains application name: " + ModuleUtils.getStaticConfig().root().toString(), ModuleUtils.getStaticConfig().root().toString().contains("application_name"));
			assertTrue("Config contains v1_enabled: " + ModuleUtils.getStaticConfig().root().toString(), ModuleUtils.getStaticConfig().root().toString().contains("v1_enabled"));
			
			final StreamingEnrichmentContext test_context = _app_injector.getInstance(StreamingEnrichmentContext.class);
	
			final DataBucketBean test_bucket = BeanTemplateUtils.build(DataBucketBean.class)
													.with(DataBucketBean::_id, "test")
													.with(DataBucketBean::full_name, "/test/external-context/creation")
													.with(DataBucketBean::modified, new Date(1436194933000L))
													.with("data_schema", BeanTemplateUtils.build(DataSchemaBean.class)
															.with("search_index_schema", BeanTemplateUtils.build(DataSchemaBean.SearchIndexSchemaBean.class)
																	.done().get())
															.done().get())
													.done().get();
			
			final SharedLibraryBean library = BeanTemplateUtils.build(SharedLibraryBean.class)
					.with(SharedLibraryBean::path_name, "/test/lib")
					.done().get();

			test_context.setLibraryConfig(library);			
			
			// Empty service set:
			final String signature = test_context.getEnrichmentContextSignature(Optional.of(test_bucket), Optional.empty());
						
			final String expected_sig = "com.ikanow.aleph2.data_import.services.StreamingEnrichmentContext:{\"3fdb4bfa-2024-11e5-b5f7-727283247c7e\":\"{\\\"_id\\\":\\\"test\\\",\\\"modified\\\":1436194933000,\\\"full_name\\\":\\\"/test/external-context/creation\\\",\\\"data_schema\\\":{\\\"search_index_schema\\\":{}}}\",\"3fdb4bfa-2024-11e5-b5f7-727283247c7f\":\"{\\\"path_name\\\":\\\"/test/lib\\\"}\",\"CoreDistributedServices\":{},\"MongoDbManagementDbService\":{\"mongodb_connection\":\"localhost:9999\"},\"globals\":{\"local_cached_jar_dir\":\"file://temp/\"},\"service\":{\"CoreDistributedServices\":{\"interface\":\"com.ikanow.aleph2.distributed_services.services.ICoreDistributedServices\",\"service\":\"com.ikanow.aleph2.distributed_services.services.MockCoreDistributedServices\"},\"CoreManagementDbService\":{\"interface\":\"com.ikanow.aleph2.data_model.interfaces.data_services.IManagementDbService\",\"service\":\"com.ikanow.aleph2.management_db.services.CoreManagementDbService\"},\"ManagementDbService\":{\"interface\":\"com.ikanow.aleph2.data_model.interfaces.data_services.IManagementDbService\",\"service\":\"com.ikanow.aleph2.management_db.mongodb.services.MockMongoDbManagementDbService\"},\"SearchIndexService\":{\"interface\":\"com.ikanow.aleph2.data_model.interfaces.data_services.ISearchIndexService\",\"service\":\"com.ikanow.aleph2.search_service.elasticsearch.services.MockElasticsearchIndexService\"},\"SecurityService\":{\"interface\":\"com.ikanow.aleph2.data_model.interfaces.shared_services.ISecurityService\",\"service\":\"com.ikanow.aleph2.data_model.interfaces.shared_services.MockSecurityService\"},\"StorageService\":{\"interface\":\"com.ikanow.aleph2.data_model.interfaces.data_services.IStorageService\",\"service\":\"com.ikanow.aleph2.storage_service_hdfs.services.MockHdfsStorageService\"}}}";			
			assertEquals(expected_sig, signature);

			// Check can't call multiple times
			
			// Additionals service set:

			try {
				test_context.getEnrichmentContextSignature(Optional.of(test_bucket),
												Optional.of(
														ImmutableSet.<Tuple2<Class<? extends IUnderlyingService>, Optional<String>>>builder()
															.add(Tuples._2T(IStorageService.class, Optional.empty()))
															.add(Tuples._2T(IManagementDbService.class, Optional.of("test")))
															.build()																
														)
												);
				fail("Should have errored");
			}
			catch (Exception e) {}
			// Create another injector:
			final StreamingEnrichmentContext test_context2 = _app_injector.getInstance(StreamingEnrichmentContext.class);
			test_context2.setLibraryConfig(library);			

			final String signature2 = test_context2.getEnrichmentContextSignature(Optional.of(test_bucket),
					Optional.of(
							ImmutableSet.<Tuple2<Class<? extends IUnderlyingService>, Optional<String>>>builder()
								.add(Tuples._2T(IStorageService.class, Optional.empty()))
								.add(Tuples._2T(IManagementDbService.class, Optional.of("test")))
								.build()																
							)
					);
			
			
			final String expected_sig2 = "com.ikanow.aleph2.data_import.services.StreamingEnrichmentContext:{\"3fdb4bfa-2024-11e5-b5f7-727283247c7e\":\"{\\\"_id\\\":\\\"test\\\",\\\"modified\\\":1436194933000,\\\"full_name\\\":\\\"/test/external-context/creation\\\",\\\"data_schema\\\":{\\\"search_index_schema\\\":{}}}\",\"3fdb4bfa-2024-11e5-b5f7-727283247c7f\":\"{\\\"path_name\\\":\\\"/test/lib\\\"}\",\"CoreDistributedServices\":{},\"MongoDbManagementDbService\":{\"mongodb_connection\":\"localhost:9999\"},\"globals\":{\"local_cached_jar_dir\":\"file://temp/\"},\"service\":{\"CoreDistributedServices\":{\"interface\":\"com.ikanow.aleph2.distributed_services.services.ICoreDistributedServices\",\"service\":\"com.ikanow.aleph2.distributed_services.services.MockCoreDistributedServices\"},\"CoreManagementDbService\":{\"interface\":\"com.ikanow.aleph2.data_model.interfaces.data_services.IManagementDbService\",\"service\":\"com.ikanow.aleph2.management_db.services.CoreManagementDbService\"},\"ManagementDbService\":{\"interface\":\"com.ikanow.aleph2.data_model.interfaces.data_services.IManagementDbService\",\"service\":\"com.ikanow.aleph2.management_db.mongodb.services.MockMongoDbManagementDbService\"},\"SearchIndexService\":{\"interface\":\"com.ikanow.aleph2.data_model.interfaces.data_services.ISearchIndexService\",\"service\":\"com.ikanow.aleph2.search_service.elasticsearch.services.MockElasticsearchIndexService\"},\"SecurityService\":{\"interface\":\"com.ikanow.aleph2.data_model.interfaces.shared_services.ISecurityService\",\"service\":\"com.ikanow.aleph2.data_model.interfaces.shared_services.MockSecurityService\"},\"StorageService\":{\"interface\":\"com.ikanow.aleph2.data_model.interfaces.data_services.IStorageService\",\"service\":\"com.ikanow.aleph2.storage_service_hdfs.services.MockHdfsStorageService\"},\"test\":{\"interface\":\"com.ikanow.aleph2.data_model.interfaces.data_services.IManagementDbService\",\"service\":\"com.ikanow.aleph2.management_db.mongodb.services.MockMongoDbManagementDbService\"}}}"; 
			assertEquals(expected_sig2, signature2);
			
			final IEnrichmentModuleContext test_external1a = ContextUtils.getEnrichmentContext(signature);		
			
			assertTrue("external context non null", test_external1a != null);
			
			assertTrue("external context of correct type", test_external1a instanceof StreamingEnrichmentContext);
			
			final StreamingEnrichmentContext test_external1b = (StreamingEnrichmentContext)test_external1a;
			
			assertTrue("StreamingEnrichmentContext dependencies", test_external1b._core_management_db != null);
			assertTrue("StreamingEnrichmentContext dependencies", test_external1b._distributed_services != null);
			assertTrue("StreamingEnrichmentContext dependencies", test_context._index_service != null);
			assertTrue("StreamingEnrichmentContext dependencies", test_external1b._globals != null);
			assertTrue("StreamingEnrichmentContext dependencies", test_external1b._service_context != null);
			
			assertEquals("test", test_external1b._mutable_state.bucket.get()._id());
			
			// Check that it gets cloned
			
			final IEnrichmentModuleContext test_external1a_1 = ContextUtils.getEnrichmentContext(signature);		
			
			assertTrue("external context non null", test_external1a_1 != null);
			
			assertTrue("external context of correct type", test_external1a_1 instanceof StreamingEnrichmentContext);
			
			final StreamingEnrichmentContext test_external1b_1 = (StreamingEnrichmentContext)test_external1a_1;
			
			assertEquals(test_external1b_1._distributed_services, test_external1b._distributed_services);
			
			// Finally, check I can see my extended services: 
			
			final IEnrichmentModuleContext test_external2a = ContextUtils.getEnrichmentContext(signature2);		
			
			assertTrue("external context non null", test_external2a != null);
			
			assertTrue("external context of correct type", test_external2a instanceof StreamingEnrichmentContext);
			
			final StreamingEnrichmentContext test_external2b = (StreamingEnrichmentContext)test_external2a;
			
			assertTrue("StreamingEnrichmentContext dependencies", test_external2b._core_management_db != null);
			assertTrue("StreamingEnrichmentContext dependencies", test_external2b._distributed_services != null);
			assertTrue("StreamingEnrichmentContext dependencies", test_context._index_service != null);
			assertTrue("StreamingEnrichmentContext dependencies", test_external2b._globals != null);
			assertTrue("StreamingEnrichmentContext dependencies", test_external2b._service_context != null);
			
			assertEquals("test", test_external2b._mutable_state.bucket.get()._id());
			
			assertEquals("/test/lib", test_external2b._mutable_state.library_config.get().path_name());			
			assertEquals("/test/lib", test_external2b.getModuleConfig().path_name());			
			
			assertTrue("I can see my additonal services", null != test_external2b._service_context.getService(IStorageService.class, Optional.empty()));
			assertTrue("I can see my additonal services", null != test_external2b._service_context.getService(IManagementDbService.class, Optional.of("test")));
						
			//Check some "won't work in module" calls:
			try {
				test_external2b.getEnrichmentContextSignature(null, null);
				fail("Should have errored");
			}
			catch (Exception e) {
				assertEquals(ErrorUtils.TECHNOLOGY_NOT_MODULE, e.getMessage());
			}
			try {
				test_external2b.getUnderlyingArtefacts();
				fail("Should have errored");
			}
			catch (Exception e) {
				assertEquals(ErrorUtils.TECHNOLOGY_NOT_MODULE, e.getMessage());
			}
			try {
				test_external2b.getTopologyEntryPoints(null, null);
				fail("Should have errored");
			}
			catch (Exception e) {
				assertEquals(ErrorUtils.TECHNOLOGY_NOT_MODULE, e.getMessage());
			}
			try {
				test_external2b.getTopologyStorageEndpoint(null, null);
				fail("Should have errored");
			}
			catch (Exception e) {
				assertEquals(ErrorUtils.TECHNOLOGY_NOT_MODULE, e.getMessage());
			}
			try {
				test_external2b.getTopologyErrorEndpoint(null, null);
				fail("Should have errored");
			}
			catch (Exception e) {
				assertEquals(ErrorUtils.TECHNOLOGY_NOT_MODULE, e.getMessage());
			}
			
		}
		catch (Exception e) {
			System.out.println(ErrorUtils.getLongForm("{1}: {0}", e, e.getClass()));
			fail("Threw exception");
		}
	}

	@Test
	public void test_getUnderlyingArtefacts() {
		_logger.info("run test_getUnderlyingArtefacts");
		
		final StreamingEnrichmentContext test_context = _app_injector.getInstance(StreamingEnrichmentContext.class);
		
		// (interlude: check errors if called before getSignature
		try {
			test_context.getUnderlyingArtefacts();
			fail("Should have errored");
		}
		catch (Exception e) {
			assertEquals(ErrorUtils.SERVICE_RESTRICTIONS, e.getMessage());
		}
		
		final DataBucketBean test_bucket = BeanTemplateUtils.build(DataBucketBean.class)
												.with(DataBucketBean::_id, "test")
												.with(DataBucketBean::full_name, "/test/get_underlying/artefacts")
												.with(DataBucketBean::modified, new Date())
												.with("data_schema", BeanTemplateUtils.build(DataSchemaBean.class)
														.with("search_index_schema", BeanTemplateUtils.build(DataSchemaBean.SearchIndexSchemaBean.class)
																.done().get())
														.done().get())
												.done().get();
		
		final SharedLibraryBean library = BeanTemplateUtils.build(SharedLibraryBean.class)
				.with(SharedLibraryBean::path_name, "/test/lib")
				.done().get();
		test_context.setLibraryConfig(library);		
		
		// Empty service set:
		test_context.getEnrichmentContextSignature(Optional.of(test_bucket), Optional.empty());		
		final Collection<Object> res1 = test_context.getUnderlyingArtefacts();
		final String exp1 = "class com.ikanow.aleph2.data_import.services.StreamingEnrichmentContext:class com.ikanow.aleph2.data_model.utils.ModuleUtils$ServiceContext:class com.ikanow.aleph2.distributed_services.services.MockCoreDistributedServices:class com.ikanow.aleph2.management_db.mongodb.services.MockMongoDbManagementDbService:class com.ikanow.aleph2.shared.crud.mongodb.services.MockMongoDbCrudServiceFactory:class com.ikanow.aleph2.search_service.elasticsearch.services.MockElasticsearchIndexService:class com.ikanow.aleph2.shared.crud.elasticsearch.services.MockElasticsearchCrudServiceFactory:class com.ikanow.aleph2.storage_service_hdfs.services.MockHdfsStorageService:class com.ikanow.aleph2.management_db.services.CoreManagementDbService:class com.ikanow.aleph2.management_db.mongodb.services.MockMongoDbManagementDbService:class com.ikanow.aleph2.shared.crud.mongodb.services.MockMongoDbCrudServiceFactory";
		assertEquals(exp1, res1.stream().map(o -> o.getClass().toString()).collect(Collectors.joining(":")));
	}
	
	@Test
	public void test_misc() {
		_logger.info("run test_misc");
		
		assertTrue("Injector created", _app_injector != null);
		
		final StreamingEnrichmentContext test_context = _app_injector.getInstance(StreamingEnrichmentContext.class);
		assertEquals(Optional.empty(), test_context.getUnderlyingPlatformDriver(String.class, Optional.empty()));
		assertEquals(Optional.empty(), test_context.getBucket());
		
		try {
			test_context.emergencyQuarantineBucket(null, null);
			fail("Should have thrown exception");
		}
		catch (Exception e) {
			assertEquals(ErrorUtils.NOT_YET_IMPLEMENTED, e.getMessage());
		}
		try {
			test_context.emergencyDisableBucket(null);
			fail("Should have thrown exception");
		}
		catch (Exception e) {
			assertEquals(ErrorUtils.NOT_YET_IMPLEMENTED, e.getMessage());
		}
		try {
			test_context.logStatusForBucketOwner(null, null);
			fail("Should have thrown exception");
		}
		catch (Exception e) {
			assertEquals(ErrorUtils.NOT_YET_IMPLEMENTED, e.getMessage());
		}
		try {
			test_context.logStatusForBucketOwner(null, null, false);
			fail("Should have thrown exception");
		}
		catch (Exception e) {
			assertEquals(ErrorUtils.NOT_YET_IMPLEMENTED, e.getMessage());
		}
		// (This has now been implemented, though not ready to test yet)
//		try {
//			test_context.getBucketStatus(null);
//			fail("Should have thrown exception");
//		}
//		catch (Exception e) {
//			assertEquals(ErrorUtils.NOT_YET_IMPLEMENTED, e.getMessage());
//		}
		try {
			test_context.getNextUnusedId();
			fail("Should have thrown exception");
		}
		catch (Exception e) {
			assertEquals(ErrorUtils.NOT_SUPPORTED_IN_STREAMING_ENRICHMENT, e.getMessage());
		}
		try {
			test_context.storeErroredObject(0L, null);
			fail("Should have thrown exception");
		}
		catch (Exception e) {
			assertEquals(ErrorUtils.NOT_YET_IMPLEMENTED, e.getMessage());
		}
		try {
			test_context.getTopologyErrorEndpoint(null, null);
			fail("Should have errored");
		}
		catch (Exception e) {
			assertEquals(ErrorUtils.NOT_YET_IMPLEMENTED, e.getMessage());
		}
	}
	
	@Test
	public void test_objectEmitting() throws InterruptedException, ExecutionException, InstantiationException, IllegalAccessException, ClassNotFoundException {
		_logger.info("run test_objectEmitting");

		final StreamingEnrichmentContext test_context = _app_injector.getInstance(StreamingEnrichmentContext.class);
		
		final DataBucketBean test_bucket = BeanTemplateUtils.build(DataBucketBean.class)
				.with(DataBucketBean::_id, "test_objectemitting")
				.with(DataBucketBean::full_name, "/test/object/emitting")
				.with(DataBucketBean::modified, new Date())
				.with("data_schema", BeanTemplateUtils.build(DataSchemaBean.class)
						.with("search_index_schema", BeanTemplateUtils.build(DataSchemaBean.SearchIndexSchemaBean.class)
								.done().get())
						.done().get())
				.done().get();

		final SharedLibraryBean library = BeanTemplateUtils.build(SharedLibraryBean.class)
				.with(SharedLibraryBean::path_name, "/test/lib")
				.done().get();
		test_context.setLibraryConfig(library);
		
		// Empty service set:
		final String signature = test_context.getEnrichmentContextSignature(Optional.of(test_bucket), Optional.empty());

		@SuppressWarnings("unchecked")
		final ICrudService<DataBucketBean> raw_mock_db =
				test_context._core_management_db.getDataBucketStore().getUnderlyingPlatformDriver(ICrudService.class, Optional.empty()).get();
		raw_mock_db.deleteDatastore().get();
		assertEquals(0L, (long)raw_mock_db.countObjects().get());
		raw_mock_db.storeObject(test_bucket).get();		
		assertEquals(1L, (long)raw_mock_db.countObjects().get());
		
		final IEnrichmentModuleContext test_external1a = ContextUtils.getEnrichmentContext(signature);		

		//(internal)
//		ISearchIndexService check_index = test_context.getService(ISearchIndexService.class, Optional.empty()).get();
		//(external)
		final ISearchIndexService check_index = test_external1a.getServiceContext().getService(ISearchIndexService.class, Optional.empty()).get();		
		final ICrudService<JsonNode> crud_check_index = 
				check_index.getDataService()
					.flatMap(s -> s.getWritableDataService(JsonNode.class, test_bucket, Optional.empty(), Optional.empty()))
					.flatMap(IDataWriteService::getCrudService)
					.get();
		crud_check_index.deleteDatastore();
		Thread.sleep(2000L); // (wait for datastore deletion to flush)
		assertEquals(0, crud_check_index.countObjects().get().intValue());
		
		
		final JsonNode jn1 = _mapper.createObjectNode().put("test", "test1");
		final JsonNode jn2 = _mapper.createObjectNode().put("test", "test2");
		
		//(try some errors)
		try {
			test_external1a.emitMutableObject(0, test_external1a.convertToMutable(jn1), Optional.of(BeanTemplateUtils.build(AnnotationBean.class).done().get()));
			fail("Should have thrown exception");
		}
		catch (Exception e) {
			assertEquals(ErrorUtils.NOT_YET_IMPLEMENTED, e.getMessage());
		}
		try {
			test_external1a.emitImmutableObject(0, jn2, Optional.empty(), Optional.of(BeanTemplateUtils.build(AnnotationBean.class).done().get()));
			fail("Should have thrown exception");
		}
		catch (Exception e) {
			assertEquals(ErrorUtils.NOT_YET_IMPLEMENTED, e.getMessage());
		}
		
		test_external1a.emitMutableObject(0, test_external1a.convertToMutable(jn1), Optional.empty());
		test_external1a.emitImmutableObject(0, jn2, Optional.empty(), Optional.empty());		
		test_external1a.emitImmutableObject(0, jn2, 
				Optional.of(_mapper.createObjectNode().put("extra", "test3_extra").put("test", "test3")), 
				Optional.empty());
		
		for (int i = 0; i < 60; ++i) {
			Thread.sleep(1000L);
			if (crud_check_index.countObjects().get().intValue() >= 2) {
				_logger.info("(Found objects after " + i + " seconds)");
				break;
			}
		}
		
		//DEBUG
		//System.out.println(crud_check_index.getUnderlyingPlatformDriver(ElasticsearchContext.class, Optional.empty()).get().indexContext().getReadableIndexList(Optional.empty()));
		//System.out.println(crud_check_index.getUnderlyingPlatformDriver(ElasticsearchContext.class, Optional.empty()).get().typeContext().getReadableTypeList());
		
		assertEquals(3, crud_check_index.countObjects().get().intValue());
		assertEquals("{\"test\":\"test3\",\"extra\":\"test3_extra\"}", ((ObjectNode)
				crud_check_index.getObjectBySpec(CrudUtils.anyOf().when("test", "test3")).get().get()).remove(Arrays.asList("_id")).toString());
		
	}

	public static class TestBean {}	
	
	@Test
	public void test_objectStateRetrieval() throws InterruptedException, ExecutionException {
		_logger.info("run test_objectStateRetrieval");
		
		final StreamingEnrichmentContext test_context = _app_injector.getInstance(StreamingEnrichmentContext.class);
		final DataBucketBean bucket = BeanTemplateUtils.build(DataBucketBean.class).with("full_name", "TEST_HARVEST_CONTEXT").done().get();

		final SharedLibraryBean lib_bean = BeanTemplateUtils.build(SharedLibraryBean.class).with("path_name", "TEST_HARVEST_CONTEXT").done().get();
		test_context.setLibraryConfig(lib_bean);
		
		ICrudService<AssetStateDirectoryBean> dir_a = test_context._core_management_db.getStateDirectory(Optional.empty(), Optional.of(AssetStateDirectoryBean.StateDirectoryType.analytic_thread));
		ICrudService<AssetStateDirectoryBean> dir_e = test_context._core_management_db.getStateDirectory(Optional.empty(), Optional.of(AssetStateDirectoryBean.StateDirectoryType.enrichment));
		ICrudService<AssetStateDirectoryBean> dir_h = test_context._core_management_db.getStateDirectory(Optional.empty(), Optional.of(AssetStateDirectoryBean.StateDirectoryType.harvest));
		ICrudService<AssetStateDirectoryBean> dir_s = test_context._core_management_db.getStateDirectory(Optional.empty(), Optional.of(AssetStateDirectoryBean.StateDirectoryType.library));

		dir_a.deleteDatastore().get();
		dir_e.deleteDatastore().get();
		dir_h.deleteDatastore().get();
		dir_s.deleteDatastore().get();
		assertEquals(0, dir_a.countObjects().get().intValue());
		assertEquals(0, dir_e.countObjects().get().intValue());
		assertEquals(0, dir_h.countObjects().get().intValue());
		assertEquals(0, dir_s.countObjects().get().intValue());
		
		@SuppressWarnings("unused")
		ICrudService<TestBean> s1 = test_context.getGlobalEnrichmentModuleObjectStore(TestBean.class, Optional.of("test"));
		assertEquals(0, dir_a.countObjects().get().intValue());
		assertEquals(0, dir_e.countObjects().get().intValue());
		assertEquals(0, dir_h.countObjects().get().intValue());
		assertEquals(1, dir_s.countObjects().get().intValue());

		@SuppressWarnings("unused")
		ICrudService<TestBean> e1 = test_context.getBucketObjectStore(TestBean.class, Optional.of(bucket), Optional.of("test"), Optional.of(AssetStateDirectoryBean.StateDirectoryType.enrichment));
		assertEquals(0, dir_a.countObjects().get().intValue());
		assertEquals(1, dir_e.countObjects().get().intValue());
		assertEquals(0, dir_h.countObjects().get().intValue());
		assertEquals(1, dir_s.countObjects().get().intValue());
		
		test_context.setBucket(bucket);
		
		@SuppressWarnings("unused")
		ICrudService<TestBean> a1 = test_context.getBucketObjectStore(TestBean.class, Optional.empty(), Optional.of("test"), Optional.of(AssetStateDirectoryBean.StateDirectoryType.analytic_thread));
		assertEquals(1, dir_a.countObjects().get().intValue());
		assertEquals(1, dir_e.countObjects().get().intValue());
		assertEquals(0, dir_h.countObjects().get().intValue());
		assertEquals(1, dir_s.countObjects().get().intValue());
		
		@SuppressWarnings("unused")
		ICrudService<TestBean> h1 = test_context.getBucketObjectStore(TestBean.class, Optional.empty(), Optional.of("test"), Optional.of(AssetStateDirectoryBean.StateDirectoryType.harvest));
		assertEquals(1, dir_a.countObjects().get().intValue());
		assertEquals(1, dir_e.countObjects().get().intValue());
		assertEquals(1, dir_h.countObjects().get().intValue());
		assertEquals(1, dir_s.countObjects().get().intValue());		

		ICrudService<AssetStateDirectoryBean> dir_e_2 = test_context.getBucketObjectStore(AssetStateDirectoryBean.class, Optional.empty(), Optional.empty(), Optional.empty());
		assertEquals(1, dir_e_2.countObjects().get().intValue());
		
		@SuppressWarnings("unused")
		ICrudService<TestBean> h2 = test_context.getBucketObjectStore(TestBean.class, Optional.empty(), Optional.of("test_2"), Optional.empty());
		assertEquals(1, dir_a.countObjects().get().intValue());
		assertEquals(2, dir_e.countObjects().get().intValue());
		assertEquals(1, dir_h.countObjects().get().intValue());
		assertEquals(1, dir_s.countObjects().get().intValue());		
		assertEquals(2, dir_e_2.countObjects().get().intValue());
		
		@SuppressWarnings("unused")
		ICrudService<TestBean> s2 = test_context.getBucketObjectStore(TestBean.class, Optional.empty(), Optional.of("test_2"), Optional.of(AssetStateDirectoryBean.StateDirectoryType.library));
		assertEquals(1, dir_a.countObjects().get().intValue());
		assertEquals(2, dir_e.countObjects().get().intValue());
		assertEquals(1, dir_h.countObjects().get().intValue());
		assertEquals(2, dir_s.countObjects().get().intValue());
		
	}
}
