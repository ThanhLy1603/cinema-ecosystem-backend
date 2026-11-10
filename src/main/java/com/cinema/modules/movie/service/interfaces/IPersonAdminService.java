package com.cinema.modules.movie.service.interfaces;

import com.cinema.common.response.PageResponse;
import com.cinema.modules.movie.dto.request.PersonRequest;
import com.cinema.modules.movie.dto.response.PersonOptionResponse;
import com.cinema.modules.movie.dto.response.PersonResponse;

import java.util.List;

public interface IPersonAdminService {

    PageResponse<PersonResponse> searchPersons(String keyword, int page, int size);

    List<PersonOptionResponse> listPersonOptions();

    PersonResponse createPerson(PersonRequest personRequest);

    PersonResponse updatePerson(Long personId, PersonRequest personRequest);

    void deletePerson(Long personId);
}
