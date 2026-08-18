package com.snef.sgbf.fiph.repository;

import com.snef.sgbf.fiph.entity.Signature;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acces en ecriture (creation uniquement) aux signatures - voir {@link Signature}, append-only. */
public interface SignatureRepository extends JpaRepository<Signature, Long> {
}
