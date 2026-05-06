package org.yechan.member

class RefreshTokenRepositoryImpl(
    private val refreshTokenJpaRepository: RefreshTokenJpaRepository,
) : RefreshTokenRepository {
    override fun replace(refreshToken: RefreshTokenModel) {
        refreshTokenJpaRepository.deleteByUserId(refreshToken.userId)
        refreshTokenJpaRepository.save(RefreshTokenEntity.from(refreshToken))
    }

    override fun findByUserId(userId: Long): RefreshTokenModel? = refreshTokenJpaRepository.findByUserId(userId)?.toDomain()

    override fun findByTokenHash(tokenHash: String): RefreshTokenModel? = refreshTokenJpaRepository.findByTokenHash(tokenHash)?.toDomain()

    override fun deleteByUserId(userId: Long) {
        refreshTokenJpaRepository.deleteByUserId(userId)
    }
}
