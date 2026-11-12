package com.proco.simulator;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MigrationUpgradeTest {
    @Test void v3ChangesSchemaWithoutRewritingAnyV2Rows() {
        String url=System.getenv().getOrDefault("TEST_DB_URL","jdbc:postgresql://localhost:5432/proco_test");
        String user=System.getenv().getOrDefault("DB_USER","proco");
        String password=System.getenv().getOrDefault("DB_PASSWORD","proco-local");
        var admin=new JdbcTemplate(new DriverManagerDataSource(url,user,password));
        String schema="upgrade_"+UUID.randomUUID().toString().replace("-","");
        admin.execute("create schema "+schema);
        try {
            String isolatedUrl=url+(url.contains("?")?"&":"?")+"currentSchema="+schema;
            var source=new DriverManagerDataSource(isolatedUrl,user,password);
            Flyway.configure().dataSource(source).schemas(schema).target("2").load().migrate();
            var db=new JdbcTemplate(source);
            db.update("insert into app_user values ('legacy','unchanged-hash','WAREHOUSE')");
            UUID released=UUID.randomUUID(),held=UUID.randomUUID(),key=UUID.randomUUID();
            db.update("insert into lot(id,order_code,supplier_lot,production_date,quantity,location,quality_state,inventory_state,sample_code,result_value,tested_by,version) values (?,'OC-H-001','LEGACY-RELEASED','2026-01-01',10,'Q-OLD','RELEASED','AVAILABLE','M-OLD',15,'legacy',42)",released);
            db.update("insert into lot(id,order_code,supplier_lot,production_date,quantity,location,quality_state,inventory_state,version) values (?,'OC-H-001','LEGACY-HELD','2026-01-01',20,'Q-OLD','HELD','BLOCKED',7)",held);
            db.update("insert into audit_event(id,lot_id,actor,role,event_type,reason,evidence,rule_version,correlation_id,payload) values (?,?,'legacy','WAREHOUSE','LEGACY_EVENT','No rewrite','EV-OLD','SIM-1',?,'original')",UUID.randomUUID(),released,key);
            db.update("insert into command_result values ('legacy',?,'original fingerprint',?)",key,released);
            var tables=List.of("app_user","specification","purchase_order","lot","audit_event","command_result");
            Map<String,List<String>> before=new HashMap<>();
            for(String table:tables)before.put(table,rows(db,table));
            Flyway.configure().dataSource(source).schemas(schema).target("3").load().migrate();
            for(String table:tables)assertEquals(before.get(table),rows(db,table),table+" must retain all original rows");
            assertEquals(0,db.queryForObject("select count(*) from inventory_hold",Integer.class));
            assertEquals(0,db.queryForObject("select count(*) from non_conformity",Integer.class));
            assertEquals(3,db.queryForObject("select max(version::integer) from flyway_schema_history where success",Integer.class));
        } finally {
            // Only remove the uniquely named synthetic schema created by this test.
            admin.execute("drop schema "+schema+" cascade");
        }
    }
    private List<String> rows(JdbcTemplate db,String table) {
        return db.queryForList("select row_to_json(t)::text from "+table+" t order by row_to_json(t)::text",String.class);
    }
}
