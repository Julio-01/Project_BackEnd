package com.product.api.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.product.api.dto.DtoProductList;

@Repository
public interface RepoProductList extends JpaRepository<DtoProductList, Integer> {
	
	List<DtoProductList> findByStatus(Integer status);

}
