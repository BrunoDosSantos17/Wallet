package com.brunoSantos.wallet.domain;

import com.brunoSantos.position.domain.AssetPosition;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;


@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @OneToMany(mappedBy = "wallet", cascade = CascadeType.ALL)
    private List<AssetPosition> positions;

}
