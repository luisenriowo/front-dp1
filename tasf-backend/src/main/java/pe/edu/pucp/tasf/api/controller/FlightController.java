package pe.edu.pucp.tasf.api.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.tasf.api.dto.FlightResponse;
import pe.edu.pucp.tasf.service.ApiMapper;
import pe.edu.pucp.tasf.service.SimulationDataService;

@RestController
@RequestMapping("/flights")
public class FlightController {

    private final SimulationDataService simulationDataService;
    private final ApiMapper apiMapper;

    public FlightController(SimulationDataService simulationDataService, ApiMapper apiMapper) {
        this.simulationDataService = simulationDataService;
        this.apiMapper = apiMapper;
    }

    @GetMapping
    public List<FlightResponse> getFlights() {
        return simulationDataService.getFlights().stream()
            .map(apiMapper::toFlightResponse)
            .toList();
    }

    @GetMapping("/{id}")
    public FlightResponse getFlight(@PathVariable String id) {
        return apiMapper.toFlightResponse(simulationDataService.getFlightById(id));
    }
}
