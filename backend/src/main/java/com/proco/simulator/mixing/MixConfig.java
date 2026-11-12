package com.proco.simulator.mixing;
import org.springframework.context.annotation.*;
import java.time.Clock;
@Configuration
public class MixConfig { @Bean Clock simulationClock(){return Clock.systemUTC();} }
