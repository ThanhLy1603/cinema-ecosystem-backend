package com.cinema.modules.cinema.service.interfaces;

import com.cinema.modules.cinema.dto.request.FacilityRequest;
import com.cinema.modules.cinema.dto.response.FacilityResponse;

import java.util.List;

public interface IFacilityService {

    List<FacilityResponse> listFacilities(boolean deleted);

    FacilityResponse createFacility(FacilityRequest facilityRequest);

    FacilityResponse updateFacility(Long facilityId, FacilityRequest facilityRequest);

    void deleteFacility(Long facilityId);

    FacilityResponse restoreFacility(Long facilityId);
}
