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
package com.ikanow.aleph2.distributed_services.services;

import static org.junit.Assert.*;

import java.util.Optional;

import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.junit.Before;
import org.junit.Test;

public class TestMockCoreDistributedServices {

	protected ICoreDistributedServices _core_distributed_services;
	
	@Before
	public void setupMockCoreDistributedServices() throws Exception {
		MockCoreDistributedServices test = new MockCoreDistributedServices();
		test.setApplicationName("test_app_name");
		_core_distributed_services = test;
	}
	
	@Test
	public void testMockCoreDistributedServices() throws KeeperException, InterruptedException, Exception {		
		final CuratorFramework curator = _core_distributed_services.getCuratorFramework();		
        String path = curator.getZookeeperClient().getZooKeeper().create("/test", new byte[]{1,2,3}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        assertEquals(path, "/test");
        
        assertTrue(_core_distributed_services.waitForAkkaJoin(Optional.empty()));
        
        assertEquals("test_app_name", _core_distributed_services.getApplicationName().get());
	}
}
