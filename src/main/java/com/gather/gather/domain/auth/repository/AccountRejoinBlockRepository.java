package com.gather.gather.domain.auth.repository;

import com.gather.gather.domain.auth.entity.AccountRejoinBlock;
import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRejoinBlockRepository extends JpaRepository<AccountRejoinBlock, Long> {

    Optional<AccountRejoinBlock> findByIdentifierTypeAndIdentifierHash(
            AccountRejoinBlockIdentifierType identifierType, String identifierHash);

    boolean existsByIdentifierTypeAndIdentifierHashAndExpiresAtAfter(
            AccountRejoinBlockIdentifierType identifierType,
            String identifierHash,
            LocalDateTime now);

    /**
     * 동일 식별자의 재탈퇴는 기존 row를 재사용하므로, 보관기간 판정 기준이 되는 source user와 key version을 최신 탈퇴 기준으로 갱신한다. 차단 기간은
     * 짧아지면 안 되므로 만료 시각만 기존 값과 비교해 연장하고, created_at은 row 최초 생성 시각이므로 유지한다.
     */
    @Modifying(flushAutomatically = true)
    @Query(
            value =
                    """
                    insert into account_rejoin_block (
                        identifier_type,
                        identifier_hash,
                        key_version,
                        expires_at,
                        source_user_id,
                        created_at
                    )
                    values (
                        :identifierType,
                        :identifierHash,
                        :keyVersion,
                        :expiresAt,
                        :sourceUserId,
                        :createdAt
                    )
                    on duplicate key update
                        expires_at = greatest(expires_at, :expiresAt),
                        key_version = :keyVersion,
                        source_user_id = :sourceUserId
                    """,
            nativeQuery = true)
    int upsertExtendingExpiration(
            @Param("identifierType") String identifierType,
            @Param("identifierHash") String identifierHash,
            @Param("keyVersion") int keyVersion,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("sourceUserId") Long sourceUserId,
            @Param("createdAt") LocalDateTime createdAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select b
            from AccountRejoinBlock b
            where b.identifierType = :identifierType
              and b.identifierHash = :identifierHash
            """)
    Optional<AccountRejoinBlock> findByIdentifierForUpdate(
            @Param("identifierType") AccountRejoinBlockIdentifierType identifierType,
            @Param("identifierHash") String identifierHash);

    /**
     * 개인정보 보관기간이 끝난 재가입 제한 row를 파기한다.
     *
     * <p>기산점은 block 생성 시각이 아니라 실제 탈퇴가 완료된 시각이다. 카카오 계정은 탈퇴 접수 시점에 block이 먼저 생기고 unlink 성공 후에야
     * withdrawn_at이 채워지므로 두 시각이 다를 수 있다. 3개월은 90일이 아니라 달력 기준이고 월말 보정이 row마다 다르므로, 단일 cutoff로 역산하지
     * 않고 row별로 DATE_ADD를 적용한다. 아직 차단이 유효한 row가 지워지지 않도록 expires_at 조건을 함께 둔다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
                    delete b
                    from account_rejoin_block b
                    join users u on u.id = b.source_user_id
                    where b.expires_at <= :now
                      and u.withdrawn_at is not null
                      and date_add(u.withdrawn_at, interval 3 month) <= :now
                    """,
            nativeQuery = true)
    int deleteAllRetentionExpired(@Param("now") LocalDateTime now);
}
