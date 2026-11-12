package com.proco.simulator.nonconformity;

import com.proco.simulator.inventory.HoldStore;
import com.proco.simulator.reception.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.*;

@RestController
@RequestMapping("/api")
public class NcController {
    private final NcService service;
    private final NcStore cases;
    private final LotStore lots;
    private final HoldStore holds;
    private final JdbcTemplate db;
    public NcController(NcService service,NcStore cases,LotStore lots,HoldStore holds,JdbcTemplate db) {
        this.service=service;this.cases=cases;this.lots=lots;this.holds=holds;this.db=db;
    }
    @GetMapping("/quality-users")
    public List<String> owners() { return db.queryForList("select username from app_user where role='QUALITY' order by username",String.class); }

    @GetMapping("/lots/{lot}/nonconformities")
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<NonConformity> list(@PathVariable UUID lot) { lots.get(lot);return cases.list(lot); }

    @GetMapping("/lots/{lot}/holds")
    public List<Map<String,Object>> holds(@PathVariable UUID lot) { lots.get(lot);return holds.list(lot); }

    @PostMapping("/lots/{lot}/nonconformities")
    public Lot open(@PathVariable UUID lot,@Valid @RequestBody NcCommand c,
            @RequestHeader("Idempotency-Key") UUID key,Authentication a) {
        return service.execute(lot,null,"open",c,key,a.getName(),role(a));
    }
    @PostMapping("/lots/{lot}/nonconformities/{nc}/{action}")
    public Lot action(@PathVariable UUID lot,@PathVariable UUID nc,@PathVariable String action,
            @Valid @RequestBody NcCommand c,@RequestHeader("Idempotency-Key") UUID key,Authentication a) {
        // Opening a case is only allowed on the collection, never on a caller-chosen identifier.
        if(action.equals("open")) throw new com.proco.simulator.shared.RuleException(404,"Acción de NC inexistente");
        return service.execute(lot,nc,action,c,key,a.getName(),role(a));
    }
    private static String role(Authentication a) { return a.getAuthorities().iterator().next().getAuthority().substring(5); }
}
