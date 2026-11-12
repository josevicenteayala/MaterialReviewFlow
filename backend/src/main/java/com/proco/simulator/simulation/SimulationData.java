package com.proco.simulator.simulation;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
@Configuration
public class SimulationData {
 @Bean org.springframework.boot.ApplicationRunner demoUsers(JdbcTemplate db, BCryptPasswordEncoder encoder, com.proco.simulator.workflow.WorkflowService workflow){return args->{
  String[][] users={{"almacen","WAREHOUSE"},{"calidad","QUALITY"},{"calidad2","QUALITY"},{"tecnico","LAB_TECH"},{"aprobador","LAB_APPROVER"},{"tejeduria","PRODUCTION_REQUESTER"},{"produccion","PRODUCTION_APPROVER"},{"geo","GEO"},{"tintoreria","DYEING"}};
  for(var u:users) db.update("insert into app_user values (?,?,?) on conflict do nothing",u[0],encoder.encode("Simulacion123!"),u[1]);
  String[][] seeds={{"OC-H-001","HILO-SIM-001","11111111-1111-4111-8111-111111111111"},{"OC-E-001","ELASTANO-SIM-001","22222222-2222-4222-8222-222222222222"}};
  for(var seed:seeds) {
   var command=new com.proco.simulator.workflow.Command(0L,"Recepción sintética inicial","OC y checklist ficticios SIM-001",seed[0],seed[1],java.time.LocalDate.of(2026,1,1),new java.math.BigDecimal("100"),"CUARENTENA-A",null,null,null);
   workflow.execute(null,"create",command,java.util.UUID.fromString(seed[2]),"almacen","WAREHOUSE");
  }
  String[][] mixingSeeds={{"OC-H-001","HILO-MIX-001"},{"OC-H-001","HILO-MIX-002"},{"OC-E-001","ELASTANO-MIX-001"}};
  for(var seed:mixingSeeds){
   var receipt=new com.proco.simulator.workflow.Command(0L,"Recepción ficticia Sprint 3","EV-SIM3",seed[0],seed[1],java.time.LocalDate.of(2026,1,1),new java.math.BigDecimal("25"),"SIM3",null,null,null);
   var id=workflow.execute(null,"create",receipt,seedKey(seed[1]+":create"),"almacen","WAREHOUSE").id();
   String[] actions={"inspect","sample","test","release","available"};
   String[] actors={"calidad","almacen","tecnico","aprobador","almacen"};
   String[] roles={"QUALITY","WAREHOUSE","LAB_TECH","LAB_APPROVER","WAREHOUSE"};
   for(int i=0;i<actions.length;i++){
    var command=new com.proco.simulator.workflow.Command((long)i,"Paso ficticio Sprint 3 "+actions[i],"EV-SIM3",null,null,null,null,null,i==0?true:null,i==1?"M-"+seed[1]:null,i==2?new java.math.BigDecimal(seed[0].equals("OC-H-001")?"15":"500"):null);
    workflow.execute(id,actions[i],command,seedKey(seed[1]+":"+actions[i]),actors[i],roles[i]);
   }
  }
 };}
 private static java.util.UUID seedKey(String value){return java.util.UUID.nameUUIDFromBytes(("SIM3:"+value).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
}
