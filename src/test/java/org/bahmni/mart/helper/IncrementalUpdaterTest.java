package org.bahmni.mart.helper;

import org.bahmni.mart.table.FormTableMetadataGenerator;
import org.bahmni.mart.table.SpecialCharacterResolver;
import org.bahmni.mart.table.domain.TableColumn;
import org.bahmni.mart.table.domain.TableData;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.bahmni.mart.CommonTestHelper.setValuesForMemberFields;
import static org.bahmni.mart.table.FormTableMetadataGenerator.getProcessedName;
import static org.bahmni.mart.table.SpecialCharacterResolver.getUpdatedTableNameIfExist;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;

@PrepareForTest(SpecialCharacterResolver.class)
@RunWith(PowerMockRunner.class)
public class IncrementalUpdaterTest {

    private IncrementalUpdater incrementalUpdater;
    private String jobName;

    @Mock
    private JdbcTemplate openmrsJdbcTemplate;

    @Mock
    private JdbcTemplate martJdbcTemplate;

    @Mock
    private MarkerManager markerManager;

    @Mock
    private TableDataGenerator tableDataGenerator;

    @Mock
    private FormTableMetadataGenerator formTableMetadataGenerator;

    @Spy
    private Map<String, Boolean> metaDataChangeMap = new HashMap<>();


    private String readerSql;
    private String updateOn;
    private String category;
    private String tableName;
    private String queryForEventObjects;
    private String queryForIds;
    private String queryForMaxEventRecordId;

    @Before
    public void setUp() throws Exception {
        incrementalUpdater = new IncrementalUpdater();
        jobName = "JobName";
        category = "CategoryName";
        tableName = "encounter";
        readerSql = "SELECT id, name FROM test";

        setValuesForMemberFields(incrementalUpdater, "openmrsJdbcTemplate", openmrsJdbcTemplate);
        setValuesForMemberFields(incrementalUpdater, "martJdbcTemplate", martJdbcTemplate);
        setValuesForMemberFields(incrementalUpdater, "markerManager", markerManager);
        setValuesForMemberFields(incrementalUpdater, "tableDataGenerator", tableDataGenerator);
        setValuesForMemberFields(incrementalUpdater, "formTableMetadataGenerator", formTableMetadataGenerator);
        setValuesForMemberFields(incrementalUpdater, "metaDataChangeMap", metaDataChangeMap);
        updateOn = "id";
        queryForEventObjects = "SELECT DISTINCT substring_index(substring_index(object, '/', -1), '?', 1) as uuid " +
                "FROM event_records WHERE id > %s AND category = '%s'";
        queryForIds = String.format("SELECT %s_id FROM %s WHERE uuid in ('%s','%s')",
                tableName, tableName,
                "2c2ca648-3fae-4719-a164-3b603b7d6d41",
                "a76f74db-9ec4-4d20-9fc9-cb449ab331a5");
        queryForMaxEventRecordId = "SELECT MAX(id) FROM event_records";

        mockStatic(SpecialCharacterResolver.class);
    }

    @Test
    public void shouldReturnSameReaderSqlWhenThereIsNoMarkerMapForGivenJob() {
        when(markerManager.getJobMarkerMap(jobName)).thenReturn(Optional.empty());

        String updatedReaderSql = incrementalUpdater.updateReaderSql(readerSql, jobName, updateOn);

        verify(markerManager).getJobMarkerMap(jobName);
        assertEquals(readerSql, updatedReaderSql);
    }

    @Test
    public void shouldReturnSameReaderSqlWhenEventRecordIdForGivenJobIsZero() {
        Map markerMap = mock(Map.class);
        when(markerMap.get("event_record_id")).thenReturn("0");
        when(markerManager.getJobMarkerMap(jobName)).thenReturn(Optional.of(markerMap));

        String actualUpdatedReaderSql = incrementalUpdater.updateReaderSql(readerSql, jobName, updateOn);

        verify(markerManager).getJobMarkerMap(jobName);
        verify(markerMap).get("event_record_id");
        assertEquals(readerSql, actualUpdatedReaderSql);
    }

    @Test
    public void shouldQueryFromEventRecordsTableWhenEventRecordIdIsNotZeroForGivenJob() {
        Map markerMap = mock(Map.class);
        when(markerMap.get("event_record_id")).thenReturn("10");
        when(markerMap.get("category")).thenReturn(category);
        when(markerManager.getJobMarkerMap(jobName)).thenReturn(Optional.of(markerMap));

        incrementalUpdater.updateReaderSql(readerSql, jobName, updateOn);

        verify(markerManager).getJobMarkerMap(jobName);
        verify(markerMap, times(2)).get("event_record_id");
        verify(markerMap).get("category");
        verify(openmrsJdbcTemplate).queryForList(String.format(queryForEventObjects, "10", category), String.class);
    }

    @Test
    public void shouldQueryFromSpecificTableToFetchIdsForGivenUuids() {
        setUpForMarkerMap();
        setUpForUuids();

        incrementalUpdater.updateReaderSql(readerSql, jobName, updateOn);

        verify(openmrsJdbcTemplate).queryForList(String.format(queryForEventObjects, "10", category), String.class);
        verify(openmrsJdbcTemplate).queryForList(queryForIds, String.class);
    }


    @Test
    public void shouldUpdateReaderSqlGivenNonZeroEventRecordIdAndNonEmptyUuidsList() {
        setUpForMarkerMap();
        setUpForUuids();
        List<String> ids = Arrays.asList("1", "2");
        when(openmrsJdbcTemplate.queryForList(queryForIds, String.class)).thenReturn(ids);
        String expectedUpdatedReaderSql = String.format("SELECT * FROM ( %s ) result WHERE %s IN (1,2)", readerSql,
                updateOn);

        String updatedReaderSql = incrementalUpdater.updateReaderSql(readerSql, jobName, updateOn);

        assertEquals(expectedUpdatedReaderSql, updatedReaderSql);
    }

    @Test
    public void shouldUpdateReaderSqlWithNonExistedIdWhenUuidListIsEmpty() {
        setUpForMarkerMap();
        when(openmrsJdbcTemplate.queryForList(String.format(queryForEventObjects, "10", category), String.class))
                .thenReturn(new ArrayList<>());
        String expectedUpdatedReaderSql = String.format("SELECT * FROM ( %s ) result WHERE %s IN (-1)", readerSql,
                updateOn);

        String updatedReaderSql = incrementalUpdater.updateReaderSql(readerSql, jobName, updateOn);

        assertEquals(expectedUpdatedReaderSql, updatedReaderSql);
    }

    @Test
    public void shouldExecuteDeleteSqlForGivenIds() {
        Set<String> ids = new HashSet<>(Arrays.asList("1", "2"));
        String table = "table";
        String column = "column";

        incrementalUpdater.deleteVoidedRecords(ids, table, column);

        verify(martJdbcTemplate).execute("DELETE FROM table WHERE column IN (1,2)");
    }

    @Test
    public void shouldNotExecuteDeleteSqlWhenIdListIsNull() {

        incrementalUpdater.deleteVoidedRecords(null, "table", "column");

        verify(martJdbcTemplate, never()).execute(anyString());
    }

    @Test
    public void shouldNotExecuteDeleteSqlWhenIdListIsEmpty() {

        incrementalUpdater.deleteVoidedRecords(Collections.emptySet(), "table", "column");

        verify(martJdbcTemplate, never()).execute(anyString());
    }

    @Test
    public void shouldCallUpdateMarkerWithMaxEventRecordIdForGivenJobName() {
        String jobName = "obs";
        when(openmrsJdbcTemplate.queryForObject(queryForMaxEventRecordId, String.class)).thenReturn("145678");

        incrementalUpdater.updateMarker(jobName);

        verify(openmrsJdbcTemplate).queryForObject(queryForMaxEventRecordId, String.class);
        verify(markerManager).updateMarker(jobName, "145678");
    }

    @Test
    public void shouldCallUpdateMarkerWithZeroWhenMaxMarkerIdIsNull() {
        String jobName = "obs";
        when(openmrsJdbcTemplate.queryForObject(queryForMaxEventRecordId, String.class)).thenReturn(null);

        incrementalUpdater.updateMarker(jobName);

        verify(openmrsJdbcTemplate).queryForObject(queryForMaxEventRecordId, String.class);
        verify(markerManager).updateMarker(jobName, "0");
    }

    @Test
    public void shouldNotQueryForMaxEventRecordIdWhenTheSameFieldIsNotNull() throws Exception {
        setValuesForMemberFields(incrementalUpdater, "maxEventRecordId", "123");

        incrementalUpdater.updateMarker("jobName");

        verify(openmrsJdbcTemplate, never()).queryForObject(queryForMaxEventRecordId, String.class);
    }

    @Test
    public void shouldReturnTrueWhenThereIsMetaDataChange() {
        String formName = "form, name@one";
        String actualTableName = getProcessedName(formName);
        TableData tableData = new TableData(actualTableName);
        List<TableColumn> tableColumns = new ArrayList<>();
        tableColumns.add(new TableColumn("id_form_name_one", "integer", true, null));
        tableColumns.add(new TableColumn("field_1", "int", false, null));
        tableData.setColumns(tableColumns);

        String updatedTableName = "form_name_one";
        when(getUpdatedTableNameIfExist(actualTableName)).thenReturn(updatedTableName);
        when(tableDataGenerator.getTableDataFromMart(updatedTableName, "SELECT * FROM form_name_one"))
                .thenReturn(new TableData());
        when(formTableMetadataGenerator.getTableDataByName(actualTableName))
                .thenReturn(tableData);

        boolean metaDataChanged = incrementalUpdater.isMetaDataChanged(formName);

        assertTrue(metaDataChanged);
        verifyStatic();
        getUpdatedTableNameIfExist(actualTableName);

        verify(tableDataGenerator).getTableDataFromMart(updatedTableName, "SELECT * FROM form_name_one");
        verify(formTableMetadataGenerator).getTableDataByName(actualTableName);
        assertFalse(metaDataChangeMap.isEmpty());
        verify(metaDataChangeMap).put("form,_name@one", true);
    }

    @Test
    public void shouldReturnFalseWhenThereIsNoMetaDataChange() {
        String formName = "form, name@one";
        String actualTableName = getProcessedName(formName);
        TableData tableData = new TableData(actualTableName);
        List<TableColumn> tableColumns = new ArrayList<>();
        tableColumns.add(new TableColumn("id_form_name_one", "integer", true, null));
        tableColumns.add(new TableColumn("field_1", "int", false, null));
        tableData.setColumns(tableColumns);

        String updatedTableName = "form_name_one";
        when(getUpdatedTableNameIfExist(actualTableName)).thenReturn(updatedTableName);
        when(tableDataGenerator.getTableDataFromMart(updatedTableName, "SELECT * FROM form_name_one"))
                .thenReturn(tableData);
        when(formTableMetadataGenerator.getTableDataByName(actualTableName))
                .thenReturn(tableData);

        boolean metaDataChanged = incrementalUpdater.isMetaDataChanged(formName);

        assertFalse(metaDataChanged);
        verifyStatic();
        getUpdatedTableNameIfExist(actualTableName);

        verify(tableDataGenerator).getTableDataFromMart(updatedTableName, "SELECT * FROM form_name_one");
        verify(formTableMetadataGenerator).getTableDataByName(actualTableName);
        assertFalse(metaDataChangeMap.isEmpty());
        verify(metaDataChangeMap).put("form,_name@one", false);
    }

    @Test
    public void shouldReturnFromMetaDataChangeMapWhenTheEntryIsStoredInMap() {
        String formName = "form, name@one";
        String actualTableName = getProcessedName(formName);
        metaDataChangeMap.put(actualTableName, true);

        assertTrue(incrementalUpdater.isMetaDataChanged(formName));

        verify(metaDataChangeMap).get(actualTableName);
        verifyStatic(never());
        getUpdatedTableNameIfExist(actualTableName);

        verify(tableDataGenerator, never()).getTableDataFromMart(anyString(), anyString());
        verify(formTableMetadataGenerator, never()).getTableDataByName(actualTableName);
    }

    @Test
    public void shouldNotThrowBadSalGrammarExceptionWhenTheTableIsNotPresent() {
        String formName = "table, name";
        String actualTableName = getProcessedName(formName);
        when(formTableMetadataGenerator.getTableDataByName(actualTableName)).thenReturn(new TableData(actualTableName));
        when(getUpdatedTableNameIfExist(actualTableName)).thenReturn("table_name");
        when(tableDataGenerator.getTableDataFromMart("table_name", "SELECT * FROM table_name"))
                .thenThrow(BadSqlGrammarException.class);

        boolean metaDataChanged = incrementalUpdater.isMetaDataChanged(formName);

        assertTrue(metaDataChanged);
        verifyStatic();
        getUpdatedTableNameIfExist(actualTableName);
        verify(tableDataGenerator).getTableDataFromMart("table_name", "SELECT * FROM table_name");
    }

    private void setUpForMarkerMap() {
        Map markerMap = mock(Map.class);
        when(markerMap.get("event_record_id")).thenReturn("10");
        when(markerMap.get("category")).thenReturn(category);
        when(markerMap.get("table_name")).thenReturn(tableName);
        when(markerManager.getJobMarkerMap(jobName)).thenReturn(Optional.of(markerMap));
    }

    private void setUpForUuids() {
        List<String> eventRecordObjects = new ArrayList<>();
        eventRecordObjects.add("2c2ca648-3fae-4719-a164-3b603b7d6d41");
        eventRecordObjects.add("a76f74db-9ec4-4d20-9fc9-cb449ab331a5");
        when(openmrsJdbcTemplate.queryForList(String.format(queryForEventObjects, "10", category), String.class))
                .thenReturn(eventRecordObjects);
    }
}
