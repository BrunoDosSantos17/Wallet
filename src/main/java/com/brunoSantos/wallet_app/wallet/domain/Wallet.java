package com.brunoSantos.wallet_app.wallet.domain;

import com.brunoSantos.wallet_app.position.domain.AssetPosition;
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
