package pe.edu.pucp.tasf.api.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.tasf.api.dto.AirportResponse;
import pe.edu.pucp.tasf.service.ApiMapper;
import pe.edu.pucp.tasf.service.SimulationDataService;

@RestController
@RequestMapping("/airports")
public class AirportController {

    private final SimulationDataService simulationDataService;
    private final ApiMapper apiMapper;

    public AirportController(SimulationDataService simulationDataService, ApiMapper apiMapper) {
        this.simulationDataService = simulationDataService;
        this.apiMapper = apiMapper;
    }

    @GetMapping
    public List<AirportResponse> getAirports() {
        return simulationDataService.getAirports().stream()
            .map(apiMapper::toAirportResponse)
            .toList();
    }

    @GetMapping("/{id}")
    public AirportResponse getAirport(@PathVariable String id) {
        return apiMapper.toAirportResponse(simulationDataService.getAirportByCode(id));
    }
}
