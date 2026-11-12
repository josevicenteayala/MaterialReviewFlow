package com.proco.simulator;

import com.proco.simulator.nonconformity.NcStore;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class NcStoreQueryCountTest {
    @Test void queryCountStaysBoundedAndEveryOpinionRemainsInItsOrderedCase() {
        String url=System.getenv().getOrDefault("TEST_DB_URL","jdbc:postgresql://localhost:5432/proco_test");
        String user=System.getenv().getOrDefault("DB_USER","proco");
        String password=System.getenv().getOrDefault("DB_PASSWORD","proco-local");
        var admin=new JdbcTemplate(new DriverManagerDataSource(url,user,password));
        String schema="nc_read_"+UUID.randomUUID().toString().replace("-","");
        admin.execute("create schema "+schema);
        try {
            String isolated=url+(url.contains("?")?"&":"?")+"currentSchema="+schema;
            var source=new DriverManagerDataSource(isolated,user,password);
            Flyway.configure().dataSource(source).schemas(schema).load().migrate();
            var db=new JdbcTemplate(source);
            db.update("insert into app_user values ('reporter','test-only','WAREHOUSE'),('owner','test-only','QUALITY'),('reviewer','test-only','LAB_APPROVER')");
            UUID empty=lot(db,"EMPTY"), single=lot(db,"SINGLE"), multiple=lot(db,"MULTIPLE");
            List<UUID> ids=new ArrayList<>();
            // Insert in reverse order at identical timestamps to exercise the ID tie-breaker.
            for(int i=9;i>=0;i--) {
                UUID id=new UUID(0,i+1);ids.add(id);
                nc(db,multiple,id,"Case "+i);
                if(i%3!=0) opinions(db,id,i+1);
            }
            Collections.reverse(ids);
            UUID singleCase=new UUID(0,100);nc(db,single,singleCase,"Single");opinions(db,singleCase,100);
            var counted=new CountingSource(source);
            var store=new NcStore(new JdbcTemplate(counted));

            counted.selects.set(0);
            assertTrue(store.list(empty).isEmpty());
            assertEquals(1,counted.selects.get(),"An empty lot needs only the cases query");

            counted.selects.set(0);
            var one=store.list(single);
            assertEquals(2,counted.selects.get());
            assertEquals(1,one.size());assertEquals(3,one.getFirst().opinions().size());

            counted.selects.set(0);
            var many=store.list(multiple);
            assertEquals(2,counted.selects.get(),"Ten cases must not execute eleven SELECT statements");
            assertEquals(ids,many.stream().map(nc->nc.id()).toList());
            assertEquals(18,many.stream().mapToInt(nc->nc.opinions().size()).sum());
            for(int i=0;i<many.size();i++) {
                var nc=many.get(i);
                assertEquals(multiple,nc.lotId());assertEquals("Case "+i,nc.reason());
                assertEquals("owner",nc.responsible());assertEquals("reporter",nc.openedBy());
                assertEquals("EV-"+i,nc.evidence());assertEquals("OPEN",nc.status());assertNull(nc.disposition());
                if(i%3==0) { assertTrue(nc.opinions().isEmpty());continue; }
                assertEquals(List.of(1L,3L,3L),nc.opinions().stream().map(o->((Number)o.get("based_on_version")).longValue()).toList());
                assertEquals(List.of(new UUID(i+1,2),new UUID(i+1,1),new UUID(i+1,3)),nc.opinions().stream().map(o->o.get("id")).toList());
                for(var opinion:nc.opinions()) {
                    assertEquals(nc.id(),opinion.get("nc_id"));
                    assertEquals("reviewer",opinion.get("author"));
                    assertEquals("CONFORMING",opinion.get("outcome"));
                    assertEquals("Opinion "+(i+1),opinion.get("reason"));
                    assertEquals("EV-OP-"+(i+1),opinion.get("evidence"));
                    assertNotNull(opinion.get("created_at"));
                }
            }
        } finally {
            // Only this test's synthetic schema is removed; existing data is never touched.
            admin.execute("drop schema "+schema+" cascade");
        }
    }

    private UUID lot(JdbcTemplate db,String name) {
        UUID id=UUID.randomUUID();
        db.update("insert into lot(id,order_code,supplier_lot,production_date,quantity,location,quality_state,inventory_state) values (?,'OC-H-001',?,'2026-01-01',1,'Q-TEST','UNDER_VERIFICATION','QUARANTINE')",id,name);
        return id;
    }
    private void nc(JdbcTemplate db,UUID lot,UUID id,String reason) {
        String evidence=reason.startsWith("Case ")?"EV-"+reason.substring(5):"EV-SINGLE";
        db.update("insert into non_conformity(id,lot_id,origin,responsible,opened_by,reason,evidence,opened_at) values (?,?,'OTHER','owner','reporter',?,?,'2026-01-01T00:00:00Z')",id,lot,reason,evidence);
    }
    private void opinions(JdbcTemplate db,UUID nc,long key) {
        for(int i:List.of(3,2,1)) {
            db.update("insert into nc_opinion(id,nc_id,author,outcome,reason,evidence,based_on_version) values (?,?,'reviewer','CONFORMING',?,?,?)",
                    new UUID(key,i),nc,"Opinion "+key,"EV-OP-"+key,i==2?1:3);
        }
    }

    /** Counts actual JDBC SELECT preparations while forwarding every operation to PostgreSQL. */
    private static final class CountingSource extends DelegatingDataSource {
        private final AtomicInteger selects=new AtomicInteger();
        CountingSource(DataSource source) { super(source); }
        @Override public Connection getConnection() throws SQLException { return count(super.getConnection()); }
        @Override public Connection getConnection(String user,String password) throws SQLException { return count(super.getConnection(user,password)); }
        private Connection count(Connection delegate) {
            return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                if(method.getName().equals("prepareStatement") && args!=null && args[0] instanceof String sql
                        && sql.stripLeading().toLowerCase(Locale.ROOT).startsWith("select")) selects.incrementAndGet();
                try { return method.invoke(delegate,args); }
                catch(InvocationTargetException e) { throw e.getCause(); }
            });
        }
    }
}
