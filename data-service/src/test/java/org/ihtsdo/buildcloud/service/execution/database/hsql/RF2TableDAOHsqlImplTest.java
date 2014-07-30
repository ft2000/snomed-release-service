package org.ihtsdo.buildcloud.service.execution.database.hsql;

import org.ihtsdo.buildcloud.service.execution.database.RF2TableDAO;
import org.ihtsdo.buildcloud.service.execution.database.RF2TableResults;
import org.ihtsdo.buildcloud.service.execution.database.map.RF2TableDAOTreeMapImpl;
import org.ihtsdo.snomed.util.rf2.schema.TableSchema;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class RF2TableDAOHsqlImplTest {

	private RF2TableDAO rf2TableDAO;
	private String rf2FullFilename;
	private String rf2DeltaFilename;

	@Before
	public void setup() throws Exception {
		rf2FullFilename = "der2_Refset_SimpleFull_INT_20130630.txt";
		rf2DeltaFilename = "rel2_Refset_SimpleDelta_INT_20130930.txt";
	}

	@Test
	public void testCreateTableHsql() throws Exception {
		rf2TableDAO = new RF2TableDAOHsqlImpl("test");
		testCreateTable();
	}

	@Test
	public void testCreateTableTreeMap() throws Exception {
		rf2TableDAO = new RF2TableDAOTreeMapImpl();
		testCreateTable();
	}

	@Test
	public void testAppendDataHsql() throws Exception {
		rf2TableDAO = new RF2TableDAOHsqlImpl("test");
		testAppendData();
	}

	@Test
	public void testAppendDataMap() throws Exception {
		rf2TableDAO = new RF2TableDAOTreeMapImpl();
		testAppendData();
	}

	private void testCreateTable() throws Exception {
		TableSchema table = rf2TableDAO.createTable(rf2FullFilename, getClass().getResourceAsStream(rf2FullFilename));

		RF2TableResults results = rf2TableDAO.selectAllOrdered(table);

		// Test first row values
		Assert.assertEquals("a895084b-10bc-42ca-912f-d70e8f0b825e\t20130130\t1\t900000000000207008\t450990004\t293495006", results.nextLine());
		Assert.assertEquals("beae078d-9e5b-4b15-a8b1-9260705afce2\t20130130\t1\t900000000000207008\t450990004\t293507007", results.nextLine());
		Assert.assertEquals("beae078d-9e5b-4b15-a8b1-9260705afce2\t20130630\t0\t900000000000207008\t450990004\t293507007", results.nextLine());
		Assert.assertEquals("347a0a38-98ab-481c-8974-fcaa6e46385c\t20130130\t1\t900000000000207008\t450990004\t62014003", results.nextLine());
		Assert.assertEquals("347a0a38-98ab-481c-8974-fcaa6e46385c\t20130630\t0\t900000000000207008\t450990004\t62014003", results.nextLine());
		Assert.assertEquals("5fa7d98a-2010-4490-bc87-7dce3a540d04\t20131230\t1\t900000000000207008\t450990004\t293104123", results.nextLine());
		Assert.assertNull(results.nextLine());
	}

	private void testAppendData() throws Exception {
		TableSchema tableSchema = rf2TableDAO.createTable(rf2FullFilename, getClass().getResourceAsStream(rf2FullFilename));

		rf2TableDAO.appendData(tableSchema, getClass().getResourceAsStream(rf2DeltaFilename));

		RF2TableResults results = rf2TableDAO.selectAllOrdered(tableSchema);
		Assert.assertEquals("a895084b-10bc-42ca-912f-d70e8f0b825e\t20130130\t1\t900000000000207008\t450990004\t293495006", results.nextLine());
		Assert.assertEquals("beae078d-9e5b-4b15-a8b1-9260705afce2\t20130130\t1\t900000000000207008\t450990004\t293507007", results.nextLine());
		Assert.assertEquals("beae078d-9e5b-4b15-a8b1-9260705afce2\t20130630\t0\t900000000000207008\t450990004\t293507007", results.nextLine());
		Assert.assertEquals("beae078d-9e5b-4b15-a8b1-9260705afce2\t20130930\t1\t900000000000207008\t450990004\t293507007", results.nextLine());
		Assert.assertEquals("347a0a38-98ab-481c-8974-fcaa6e46385c\t20130130\t1\t900000000000207008\t450990004\t62014003", results.nextLine());
		Assert.assertEquals("347a0a38-98ab-481c-8974-fcaa6e46385c\t20130630\t0\t900000000000207008\t450990004\t62014003", results.nextLine());
		Assert.assertEquals("4a926393-55f8-4cdf-95f6-d70c23185212\t20130930\t1\t900000000000207008\t450990004\t293104009", results.nextLine());
		Assert.assertEquals("5fa7d98a-2010-4490-bc87-7dce3a540d04\t20131230\t1\t900000000000207008\t450990004\t293104123", results.nextLine());
		Assert.assertNull(results.nextLine());
	}

	@After
	public void tearDown() throws Exception {
		rf2TableDAO.closeConnection();
	}

}
