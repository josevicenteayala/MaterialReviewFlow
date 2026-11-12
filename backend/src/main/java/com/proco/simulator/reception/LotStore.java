package com.proco.simulator.reception;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
@Repository
public class LotStore {
 private final JdbcTemplate db;
 public LotStore(JdbcTemplate db){this.db=db;}
 public List<Lot> all(){return db.query(SQL+" order by l.created_at desc",this::map);}
 public Lot get(UUID id){return db.query(SQL+" where l.id=?",this::map,id).stream().findFirst().orElseThrow(()->new com.proco.simulator.shared.RuleException(404,"Lote inexistente"));}
 private static final String SQL="select l.*,p.reference,p.supplier,s.test_name,s.unit,s.minimum,s.maximum,s.rule_version from lot l join purchase_order p on p.code=l.order_code join specification s on s.reference=p.reference";
 private Lot map(java.sql.ResultSet r,int n)throws java.sql.SQLException{return new Lot(r.getObject("id",UUID.class),r.getString("order_code"),r.getString("supplier_lot"),r.getDate("production_date").toLocalDate(),r.getBigDecimal("quantity"),r.getString("location"),r.getString("quality_state"),r.getString("inventory_state"),r.getLong("version"),r.getInt("cycle"),r.getString("sample_code"),r.getBigDecimal("result_value"),r.getString("tested_by"),r.getTimestamp("created_at").toInstant(),r.getString("reference"),r.getString("supplier"),r.getString("test_name"),r.getString("unit"),r.getBigDecimal("minimum"),r.getBigDecimal("maximum"),r.getString("rule_version"));}
}
