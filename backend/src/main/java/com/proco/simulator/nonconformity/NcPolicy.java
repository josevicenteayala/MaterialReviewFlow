package com.proco.simulator.nonconformity;

import com.proco.simulator.reception.Lot;
import com.proco.simulator.laboratory.LaboratoryPolicy;
import java.util.Map;
import static com.proco.simulator.shared.RuleException.require;

public final class NcPolicy {
    private NcPolicy() {}

    public static void mutable(Lot lot) {
        require(!lot.qualityState().equals("REJECTED"),409,"Rechazo definitivo: expediente material terminal");
    }

    public static void decision(Lot lot, Map<String,Object> nc, Map<String,Object> opinion, String actor, String outcome) {
        require(actor.equals(nc.get("responsible")),403,"Solo el responsable de Calidad puede decidir esta NC");
        require(!actor.equals(nc.get("opened_by")) && !actor.equals(opinion.get("author")),422,
                "La decisión requiere identidad distinta del reportante y del autor del dictamen");
        require(((Number)opinion.get("based_on_version")).longValue() == lot.version(),409,
                "Dictamen desactualizado; Laboratorio debe revisar la versión actual");
        require(outcome.equals(opinion.get("outcome")),422,"El dictamen no soporta esta disposición");
    }

    public static void liftable(Lot lot) {
        require(lot.qualityState().equals("UNDER_TEST") || lot.qualityState().equals("RELEASED"),422,
                "Debe subsanarse y reinspeccionarse el material antes de levantar el bloqueo");
        require(lot.sampleCode()!=null && lot.resultValue()!=null && LaboratoryPolicy.passes(lot,lot.resultValue()),422,
                "Levantar exige muestra y ensayo conforme vigentes");
    }
}
