package com.proco.simulator.simulation;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
@Configuration
public class SimulationData {
 @Bean org.springframework.boot.ApplicationRunner demoUsers(JdbcTemplate db, BCryptPasswordEncoder encoder, com.proco.simulator.workflow.WorkflowService workflow){return args->{
  String[][] users={{"almacen","WAREHOUSE"},{"calidad","QUALITY"},{"tecnico","LAB_TECH"},{"aprobador","LAB_APPROVER"}};
  for(var u:users) db.update("insert into app_user values (?,?,?) on conflict do nothing",u[0],encoder.encode("Simulacion123!"),u[1]);
  String[][] seeds={{"OC-H-001","HILO-SIM-001","11111111-1111-4111-8111-111111111111"},{"OC-E-001","ELASTANO-SIM-001","22222222-2222-4222-8222-222222222222"}};
  for(var seed:seeds) {
   var command=new com.proco.simulator.workflow.Command(0L,"Recepción sintética inicial","OC y checklist ficticios SIM-001",seed[0],seed[1],java.time.LocalDate.of(2026,1,1),new java.math.BigDecimal("100"),"CUARENTENA-A",null,null,null);
   workflow.execute(null,"create",command,java.util.UUID.fromString(seed[2]),"almacen","WAREHOUSE");
  }
 };}
}
