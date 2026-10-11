/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.repository.jpa;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.repository.OwnerRepository;
import org.springframework.samples.petclinic.repository.OwnerSearchCriteria;
import org.springframework.stereotype.Repository;

/**
 * JPA implementation of the {@link OwnerRepository} interface.
 *
 * @author Mike Keith
 * @author Rod Johnson
 * @author Sam Brannen
 * @author Michael Isvy
 * @author Vitaliy Fedoriv
 */
@Repository
@Profile("jpa")
public class JpaOwnerRepositoryImpl implements OwnerRepository {

    @PersistenceContext
    private EntityManager em;


    /**
     * Important: in the current version of this method, we load Owners with all their Pets and Visits while
     * we do not need Visits at all and we only need one property from the Pet objects (the 'name' property).
     * There are some ways to improve it such as:
     * - creating a Ligtweight class (example here: https://community.jboss.org/wiki/LightweightClass)
     * - Turning on lazy-loading and using open session in view pattern
     */
    @SuppressWarnings("unchecked")
    public Collection<Owner> findByLastName(String lastName) {
        // using 'join fetch' because a single query should load both owners and pets
        // using 'left join fetch' because it might happen that an owner does not have pets yet
        Query query = this.em.createQuery("SELECT DISTINCT owner FROM Owner owner left join fetch owner.pets WHERE owner.lastName LIKE :lastName");
        query.setParameter("lastName", lastName + "%");
        return query.getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Page<Owner> findByLastName(String lastName, Pageable pageable) throws DataAccessException {
        Query query = this.em.createQuery("SELECT owner FROM Owner owner WHERE owner.lastName LIKE :lastName ORDER BY owner.id");
        query.setParameter("lastName", lastName + "%");
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());
        List<Owner> owners = query.getResultList();
        Query countQuery = this.em.createQuery("SELECT COUNT(owner) FROM Owner owner WHERE owner.lastName LIKE :lastName");
        countQuery.setParameter("lastName", lastName + "%");
        long total = (long) countQuery.getSingleResult();
        return new PageImpl<>(owners, pageable, total);
    }

    @Override
    public Owner findById(int id) {
        // using 'join fetch' because a single query should load both owners and pets
        // using 'left join fetch' because it might happen that an owner does not have pets yet
        Query query = this.em.createQuery("SELECT owner FROM Owner owner left join fetch owner.pets WHERE owner.id =:id");
        query.setParameter("id", id);
        return (Owner) query.getSingleResult();
    }


    @Override
    public void save(Owner owner) {
        if (owner.getId() == null) {
            this.em.persist(owner);
        } else {
            this.em.merge(owner);
        }

    }

	@SuppressWarnings("unchecked")
	@Override
	public Collection<Owner> findAll() throws DataAccessException {
		Query query = this.em.createQuery("SELECT owner FROM Owner owner");
        return query.getResultList();
	}

    @SuppressWarnings("unchecked")
    @Override
    public Page<Owner> findAll(@NonNull Pageable pageable) throws DataAccessException {
        Query query = this.em.createQuery("SELECT owner FROM Owner owner ORDER BY owner.id");
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());
        List<Owner> owners = query.getResultList();
        Query countQuery = this.em.createQuery("SELECT COUNT(owner) FROM Owner owner");
        long total = (long) countQuery.getSingleResult();
        return new PageImpl<>(owners, pageable, total);
    }

	@Override
	public void delete(Owner owner) throws DataAccessException {
		this.em.remove(this.em.contains(owner) ? owner : this.em.merge(owner));
	}

    @Override
    @SuppressWarnings("unchecked")
    public Page<Owner> search(OwnerSearchCriteria criteria, Pageable pageable) throws DataAccessException {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        Map<String, Object> params = new HashMap<>();
        if (criteria.lastName() != null) {
            where.append(" AND owner.lastName LIKE :lastName");
            params.put("lastName", criteria.lastName() + "%");
        }
        if (criteria.city() != null) {
            where.append(" AND owner.city = :city");
            params.put("city", criteria.city());
        }

        Query query = this.em.createQuery("SELECT owner FROM Owner owner" + where + orderBy(pageable.getSort()));
        Query countQuery = this.em.createQuery("SELECT COUNT(owner) FROM Owner owner" + where);
        params.forEach((name, value) -> {
            query.setParameter(name, value);
            countQuery.setParameter(name, value);
        });
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());
        List<Owner> owners = query.getResultList();
        long total = (long) countQuery.getSingleResult();
        return new PageImpl<>(owners, pageable, total);
    }

    private static String orderBy(Sort sort) {
        Sort.Order order = sort.stream().findFirst().orElse(Sort.Order.asc("id"));
        String property = switch (order.getProperty()) {
            case "lastName" -> "owner.lastName";
            case "city" -> "owner.city";
            default -> "owner.id";
        };
        return " ORDER BY " + property + (order.isDescending() ? " DESC" : " ASC") + ", owner.id";
    }
}
