/*******************************************************************************
 * Copyright 2015, The IKANOW Open Source Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.ikanow.aleph2.management_db.services;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.zookeeper.CreateMode;
import org.junit.Before;
import org.junit.Test;

import scala.Tuple2;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;

import com.ikanow.aleph2.data_model.interfaces.data_services.IManagementDbService;
import com.ikanow.aleph2.data_model.interfaces.data_services.IStorageService;
import com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService;
import com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService;
import com.ikanow.aleph2.data_model.interfaces.shared_services.ISecurityService;
import com.ikanow.aleph2.data_model.interfaces.shared_services.MockSecurityService;
import com.ikanow.aleph2.data_model.interfaces.shared_services.MockServiceContext;
import com.ikanow.aleph2.data_model.objects.data_import.DataBucketBean;
import com.ikanow.aleph2.data_model.objects.data_import.DataBucketStatusBean;
import com.ikanow.aleph2.data_model.objects.data_import.HarvestControlMetadataBean;
import com.ikanow.aleph2.data_model.objects.shared.AssetStateDirectoryBean;
import com.ikanow.aleph2.data_model.objects.shared.BasicMessageBean;
import com.ikanow.aleph2.data_model.objects.shared.GlobalPropertiesBean;
import com.ikanow.aleph2.data_model.objects.shared.ProcessingTestSpecBean;
import com.ikanow.aleph2.data_model.objects.shared.SharedLibraryBean;
import com.ikanow.aleph2.data_model.utils.BeanTemplateUtils;
import com.ikanow.aleph2.data_model.utils.CrudUtils;
import com.ikanow.aleph2.data_model.utils.ErrorUtils;
import com.ikanow.aleph2.data_model.utils.ModuleUtils;
import com.ikanow.aleph2.data_model.utils.Tuples;
import com.ikanow.aleph2.data_model.utils.FutureUtils.ManagementFuture;
import com.ikanow.aleph2.data_model.utils.UuidUtils;
import com.ikanow.aleph2.distributed_services.services.ICoreDistributedServices;
import com.ikanow.aleph2.distributed_services.services.MockCoreDistributedServices;
import com.ikanow.aleph2.management_db.data_model.BucketActionMessage;
import com.ikanow.aleph2.management_db.data_model.BucketActionReplyMessage;
import com.ikanow.aleph2.management_db.data_model.BucketActionRetryMessage;
import com.ikanow.aleph2.management_db.data_model.BucketMgmtMessage.BucketDeletionMessage;
import com.ikanow.aleph2.management_db.data_model.BucketMgmtMessage.BucketTimeoutMessage;
import com.ikanow.aleph2.management_db.mongodb.data_model.MongoDbManagementDbConfigBean;
import com.ikanow.aleph2.management_db.mongodb.services.MockMongoDbManagementDbService;
import com.ikanow.aleph2.management_db.utils.ActorUtils;
import com.ikanow.aleph2.shared.crud.mongodb.services.MockMongoDbCrudServiceFactory;
import com.ikanow.aleph2.storage_service_hdfs.services.MockHdfsStorageService;

public class TestCoreManagementDbModule {
	private static final Logger _logger = LogManager.getLogger();
	
	// This is everything DI normally does for you!
	private MockMongoDbManagementDbService _underlying_db_service;
	private CoreManagementDbService _core_db_service;
	private MockServiceContext _mock_service_context;
	private MockHdfsStorageService _mock_storage_service;
	private MockMongoDbCrudServiceFactory _crud_factory;
	private DataBucketCrudService _bucket_crud;
	private DataBucketStatusCrudService _bucket_status_crud;
	private SharedLibraryCrudService _shared_library_crud;
	private MockCoreDistributedServices _cds;
	private ManagementDbActorContext _actor_context;
	
	@SuppressWarnings("deprecation")
	@Before
	public void test_Setup() throws Exception {
		ModuleUtils.disableTestInjection();
		
		// A bunch of DI related setup:
		// Here's the setup that Guice normally gives you....
		_mock_service_context = new MockServiceContext();		
		_crud_factory = new MockMongoDbCrudServiceFactory();
		_underlying_db_service = new MockMongoDbManagementDbService(_crud_factory, new MongoDbManagementDbConfigBean(false), null, null, null, null);
		final String tmpdir = System.getProperty("java.io.tmpdir") + File.separator;
		_mock_service_context.addGlobals(new GlobalPropertiesBean(tmpdir, tmpdir, tmpdir, tmpdir));
		_mock_storage_service = new MockHdfsStorageService(_mock_service_context.getGlobalProperties());
		_mock_service_context.addService(IStorageService.class, Optional.empty(), _mock_storage_service);		
		_mock_service_context.addService(IManagementDbService.class, Optional.empty(), _underlying_db_service);
		_mock_service_context.addService(ISecurityService.class, Optional.empty(), new MockSecurityService());
		_cds = new MockCoreDistributedServices();
		_cds.setApplicationName("test");
		_mock_service_context.addService(ICoreDistributedServices.class, Optional.empty(), _cds);
		_actor_context = new ManagementDbActorContext(_mock_service_context, true);
		_bucket_crud = new DataBucketCrudService(_mock_service_context, _actor_context);
		_bucket_status_crud = new DataBucketStatusCrudService(_mock_service_context, _actor_context); 
		_shared_library_crud = new SharedLibraryCrudService(_mock_service_context);		
		
		_core_db_service = new CoreManagementDbService(_mock_service_context, 
				_bucket_crud, _bucket_status_crud, _shared_library_crud, _actor_context);
		_mock_service_context.addService(IManagementDbService.class, IManagementDbService.CORE_MANAGEMENT_DB, _core_db_service);
		
		_bucket_crud.initialize();
		_bucket_status_crud.initialize();		
	}
	
	protected static String _check_actor_called = null;
	
	// This one always accepts, and returns a message
	public static class TestActor_Accepter extends UntypedActor {
		public TestActor_Accepter(String uuid) {
			this.uuid = uuid;
		}
		private final String uuid;
		@Override
		public void onReceive(Object arg0) throws Exception {
			if (arg0 instanceof BucketActionMessage.BucketActionOfferMessage) {
				_logger.info("Accept OFFER from: " + uuid);
				this.sender().tell(new BucketActionReplyMessage.BucketActionWillAcceptMessage(uuid), this.self());
			}
			else {
				_logger.info("Accept MESSAGE from: " + uuid);
				_check_actor_called = uuid;
				
				this.sender().tell(
						new BucketActionReplyMessage.BucketActionHandlerMessage(uuid, 
								new BasicMessageBean(
										new Date(),
										true,
										uuid + "replaceme", // (this gets replaced by the bucket)
										arg0.getClass().getSimpleName(),
										null,
										"handled",
										null									
										)),
						this.self());
			}
		}		
	}
	
	// This one always accepts after sleeping for 5s, and returns a message
		public static class TestActor_Accepter_Waits extends UntypedActor {
			public TestActor_Accepter_Waits(String uuid) {
				this.uuid = uuid;
			}
			private final String uuid;
			@Override
			public void onReceive(Object arg0) throws Exception {
				if (arg0 instanceof BucketActionMessage.BucketActionOfferMessage) {
					_logger.info("Accept OFFER from: " + uuid);
					this.sender().tell(new BucketActionReplyMessage.BucketActionWillAcceptMessage(uuid), this.self());
				}
				else {
					_logger.info("Accept MESSAGE from: " + uuid);
					_check_actor_called = uuid;
					Thread.sleep(5000);
					this.sender().tell(
							new BucketActionReplyMessage.BucketActionHandlerMessage(uuid, 
									new BasicMessageBean(
											new Date(),
											true,
											uuid + "replaceme", // (this gets replaced by the bucket)
											arg0.getClass().getSimpleName(),
											null,
											"handled",
											null									
											)),
							this.self());
				}
			}		
		}	
	
	public Tuple2<String,ActorRef> insertActor(Class<? extends UntypedActor> actor_clazz) throws Exception {
		String uuid = UuidUtils.get().getRandomUuid();
		ManagementDbActorContext.get().getDistributedServices()
			.getCuratorFramework().create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL)
			.forPath(ActorUtils.BUCKET_ACTION_ZOOKEEPER + "/" + uuid);
		
		ActorRef handler = ManagementDbActorContext.get().getActorSystem().actorOf(Props.create(actor_clazz, uuid), uuid);
		ManagementDbActorContext.get().getBucketActionMessageBus().subscribe(handler, ActorUtils.BUCKET_ACTION_EVENT_BUS);

		return Tuples._2T(uuid, handler);
	}
	
	public void shutdownActor(ActorRef to_remove) {
		ManagementDbActorContext.get().getBucketActionMessageBus().unsubscribe(to_remove);
	}
	
	@Test
	public void test_RetryDataStore() throws Exception {
				
		ICrudService<BucketActionRetryMessage> retry_service = _core_db_service.getRetryStore(BucketActionRetryMessage.class);
		
		assertTrue("Retry service non null", retry_service != null);
		
		// Build the message to store:
		
		final DataBucketBean bucket = BeanTemplateUtils.build(DataBucketBean.class)
				.with(DataBucketBean::_id, "id" + 1)
				.with(DataBucketBean::full_name, "/bucket/path/here/" + 1)
				.with(DataBucketBean::display_name, "Test Bucket ")
				.with(DataBucketBean::harvest_technology_name_or_id, "/app/aleph2/library/import/harvest/tech/here/" + 1)
				.with(DataBucketBean::multi_node_enabled, false)
				.with(DataBucketBean::tags, Collections.emptySet())
				.with(DataBucketBean::owner_id, UuidUtils.get().getRandomUuid())
				.done().get();
		
		final BucketActionMessage.DeleteBucketActionMessage test_message = new 
				BucketActionMessage.DeleteBucketActionMessage(bucket, Collections.emptySet());

		retry_service.deleteDatastore().get(); // (just clear it out from the past test)
		
		assertEquals(0L, (long)retry_service.countObjects().get());
		
		retry_service.storeObject(
				new BucketActionRetryMessage("test1",
						// (can't clone the message because it's not a bean, but the c'tor is very simple)
						new BucketActionMessage.DeleteBucketActionMessage(
								test_message.bucket(),
								new HashSet<String>(Arrays.asList("test1"))
								)										
						)).get(); // (get just to ensure completes)
		
		assertEquals(1L, (long)retry_service.countObjects().get());
		
		final Optional<BucketActionRetryMessage> retry = retry_service.getObjectBySpec(CrudUtils.anyOf(BucketActionRetryMessage.class)).get();
		assertTrue("Finds an object", retry.isPresent());
		assertTrue("Has an _id", retry.get()._id() != null);
		assertEquals((double)(new Date().getTime()), (double)retry.get().inserted().getTime(), 1000.0); 
		assertEquals((double)(new Date().getTime()), (double)retry.get().last_checked().getTime(), 1000.0);
		assertEquals(BucketActionMessage.DeleteBucketActionMessage.class.getName(), retry.get().message_clazz());
		assertEquals("test1", retry.get().source());
		final BucketActionMessage to_retry = (BucketActionMessage)BeanTemplateUtils.from(retry.get().message(), Class.forName(retry.get().message_clazz())).get();
		assertEquals("id1", to_retry.bucket()._id());
		assertEquals(new HashSet<String>(Arrays.asList("test1")), to_retry.handling_clients());
	}
	
	@Test
	public void test__readOnly() {
		
		final IManagementDbService read_only_management_db_service = _core_db_service.readOnlyVersion();
		
		// Bucket
		ICrudService<DataBucketBean> bucket_service = read_only_management_db_service.getDataBucketStore();
		assertTrue("Is read only", IManagementCrudService.IReadOnlyManagementCrudService.class.isAssignableFrom(bucket_service.getClass()));
		try {
			bucket_service.deleteDatastore();
			fail("Should have thrown error");
		}
		catch (Exception e) {
			assertEquals("Correct error message", ErrorUtils.READ_ONLY_CRUD_SERVICE, e.getMessage());
		}
		bucket_service.countObjects(); // (just check doesn't thrown)
		// Bucket status
		ICrudService<DataBucketStatusBean> bucket_status_service = read_only_management_db_service.getDataBucketStatusStore();
		assertTrue("Is read only", IManagementCrudService.IReadOnlyManagementCrudService.class.isAssignableFrom(bucket_status_service.getClass()));
		try {
			bucket_status_service.deleteDatastore();
			fail("Should have thrown error");
		}
		catch (Exception e) {
			assertEquals("Correct error message", ErrorUtils.READ_ONLY_CRUD_SERVICE, e.getMessage());
		}
		bucket_status_service.countObjects(); // (just check doesn't thrown)
		// Shared Library Store
		ICrudService<SharedLibraryBean> shared_lib_service = read_only_management_db_service.getSharedLibraryStore();
		assertTrue("Is read only", IManagementCrudService.IReadOnlyManagementCrudService.class.isAssignableFrom(shared_lib_service.getClass()));
		try {
			shared_lib_service.deleteDatastore();
			fail("Should have thrown error");
		}
		catch (Exception e) {
			assertEquals("Correct error message", ErrorUtils.READ_ONLY_CRUD_SERVICE, e.getMessage());
		}
		shared_lib_service.countObjects(); // (just check doesn't thrown)
		// Retry Store
		ICrudService<BucketActionRetryMessage> retry_service = read_only_management_db_service.getRetryStore(BucketActionRetryMessage.class);
		assertTrue("Is read only", ICrudService.IReadOnlyCrudService.class.isAssignableFrom(retry_service.getClass()));
		try {
			retry_service.deleteDatastore();
			fail("Should have thrown error");
		}
		catch (Exception e) {
			assertEquals("Correct error message", ErrorUtils.READ_ONLY_CRUD_SERVICE, e.getMessage());
		}
		retry_service.countObjects(); // (just check doesn't thrown)
		
		// Per-asset state:
		{
			ICrudService<AssetStateDirectoryBean> per_bucket = read_only_management_db_service.getBucketHarvestState(AssetStateDirectoryBean.class, BeanTemplateUtils.build(DataBucketBean.class).with("full_name", "/test").done().get(), Optional.empty());

			assertTrue("Is read only: " + per_bucket.getClass(), ICrudService.IReadOnlyCrudService.class.isAssignableFrom(per_bucket.getClass()));
			try {
				per_bucket.deleteDatastore();
				fail("Should have thrown error");
			}
			catch (Exception e) {
				assertEquals("Correct error message", ErrorUtils.READ_ONLY_CRUD_SERVICE, e.getMessage());
			}
			per_bucket.countObjects(); // (just check doesn't thrown)
		}	
		{
			ICrudService<AssetStateDirectoryBean> per_bucket = read_only_management_db_service.getBucketEnrichmentState(AssetStateDirectoryBean.class, BeanTemplateUtils.build(DataBucketBean.class).with("full_name", "/test").done().get(), Optional.empty());
			assertTrue("Is read only", ICrudService.IReadOnlyCrudService.class.isAssignableFrom(per_bucket.getClass()));
			try {
				per_bucket.deleteDatastore();
				fail("Should have thrown error");
			}
			catch (Exception e) {
				assertEquals("Correct error message", ErrorUtils.READ_ONLY_CRUD_SERVICE, e.getMessage());
			}
			per_bucket.countObjects(); // (just check doesn't thrown)
		}		
		{
			ICrudService<AssetStateDirectoryBean> per_bucket = read_only_management_db_service.getBucketAnalyticThreadState(AssetStateDirectoryBean.class, BeanTemplateUtils.build(DataBucketBean.class).with("full_name", "/test").done().get(), Optional.empty());
			assertTrue("Is read only", ICrudService.IReadOnlyCrudService.class.isAssignableFrom(per_bucket.getClass()));
			try {
				per_bucket.deleteDatastore();
				fail("Should have thrown error");
			}
			catch (Exception e) {
				assertEquals("Correct error message", ErrorUtils.READ_ONLY_CRUD_SERVICE, e.getMessage());
			}
			per_bucket.countObjects(); // (just check doesn't thrown)
		}		
		{
			ICrudService<AssetStateDirectoryBean> per_library = read_only_management_db_service.getPerLibraryState(AssetStateDirectoryBean.class, BeanTemplateUtils.build(SharedLibraryBean.class).with("path_name", "/test").done().get(), Optional.empty());
			assertTrue("Is read only", ICrudService.IReadOnlyCrudService.class.isAssignableFrom(per_library.getClass()));
			try {
				per_library.deleteDatastore();
				fail("Should have thrown error");
			}
			catch (Exception e) {
				assertEquals("Correct error message", ErrorUtils.READ_ONLY_CRUD_SERVICE, e.getMessage());
			}
			per_library.countObjects(); // (just check doesn't thrown)
		}		
	}
		
	/**
	 * Tests the test_bucket action by sending a fake bucket and test spec and checking that
	 * objects were submitted to the test and delete queues.  The start test message is
	 * intercepted by a fake actor (TestActor_Accepter)
	 * 
	 * @throws Exception
	 */
	@Test
	public void test_TestBucket() throws Exception {
		final ICrudService<BucketTimeoutMessage> test_queue = _core_db_service.getBucketTestQueue(BucketTimeoutMessage.class);		
		final ICrudService<BucketDeletionMessage> delete_queue = _core_db_service.getBucketDeletionQueue(BucketDeletionMessage.class);
		//clear queues before starting
		test_queue.deleteDatastore().get();
		delete_queue.deleteDatastore().get();
		
		final Tuple2<String,ActorRef> host_actor = insertActor(TestActor_Accepter.class);
		final DataBucketBean to_test = createBucket("test_tech_id", false);
		final ProcessingTestSpecBean test_spec = new ProcessingTestSpecBean(10L, 10L);
		final ManagementFuture<Boolean> future = _core_db_service.testBucket(to_test, test_spec);
		assertTrue("Fails! " + future.getManagementResults().get().stream().map(m->m.message()).collect(Collectors.joining(";")), future.get());
		future.getManagementResults().get();
		
		//check a fail:
		final DataBucketBean to_test2 = createBucket("test_tech_id", true);
		final ManagementFuture<Boolean> future2 = _core_db_service.testBucket(to_test2, test_spec);
		assertTrue("Should fail", !future2.get());
		assertTrue("Should have management errs", !future.getManagementResults().get().isEmpty());		
		
		//TOTEST:
		//1. Test Queue populated		
		assertEquals(test_queue.countObjects().get().longValue(),1);		
		
		//2. Delete queue populated		
		assertEquals(delete_queue.countObjects().get().longValue(),1);
		
		//3. Actor received test message
		assertEquals(_check_actor_called, host_actor._1());
		//3a. harvest actor
		//3b. stream actor
		
		//cleanup
		shutdownActor(host_actor._2());		
	}
	
	/**
	 * Tests the test_bucket action timeout by sending a fake bucket and setting the timeout
	 * to a tiny amount (1s).  Uses an actor that waits 5s to respond to messages, so the test
	 * will timeout before the actor responds.
	 * 
	 * @throws Exception
	 */
	@Test
	public void test_TestBucket_startup_timeout() throws Exception {
		final ICrudService<BucketTimeoutMessage> test_queue = _core_db_service.getBucketTestQueue(BucketTimeoutMessage.class);		
		final ICrudService<BucketDeletionMessage> delete_queue = _core_db_service.getBucketDeletionQueue(BucketDeletionMessage.class);
		//clear queues before starting
		test_queue.deleteDatastore().get();
		delete_queue.deleteDatastore().get();
		
		final Tuple2<String,ActorRef> host_actor = insertActor(TestActor_Accepter_Waits.class);
		final DataBucketBean to_test = createBucket("test_tech_id", false);
		//startup timeout is set to 1s
		final ProcessingTestSpecBean test_spec = new ProcessingTestSpecBean(10L, 1L, 1L, 86400L, true);
		final ManagementFuture<Boolean> future = _core_db_service.testBucket(to_test, test_spec);
		assertFalse("Test should have failed via timeout, said something the lines of 'Error, hostnames was empty, possibly did not finish setting job up...'", future.get());
		assertTrue("Odd test message" + 
				future.getManagementResults().get().stream().map(m->m.message()).collect(Collectors.joining(";"))
				, 
				future.getManagementResults().get().stream().map(m->m.message()).collect(Collectors.joining(";")).startsWith("Error, no successful actions performed"));
		
		future.getManagementResults().get();
		
		//3. Actor received test message
		assertEquals(_check_actor_called, host_actor._1());
		
		//cleanup
		shutdownActor(host_actor._2());		
	}
	
	protected DataBucketBean createBucket(final String harvest_tech_id, boolean fail) {
		return BeanTemplateUtils.build(DataBucketBean.class)
							.with(DataBucketBean::_id, "test1")
							.with(DataBucketBean::created, new Date())
							.with(DataBucketBean::modified, new Date())
							.with(DataBucketBean::display_name, "test1 display")
							.with(DataBucketBean::full_name, "/test/path" + (fail ? "/" : ""))
							.with(DataBucketBean::harvest_technology_name_or_id, harvest_tech_id)
							.with(DataBucketBean::harvest_configs, Arrays.asList(BeanTemplateUtils.build(HarvestControlMetadataBean.class).with(HarvestControlMetadataBean::enabled, true).done().get()))
							.with(DataBucketBean::owner_id, "test_owner_id")
							.done().get();
	}
}
