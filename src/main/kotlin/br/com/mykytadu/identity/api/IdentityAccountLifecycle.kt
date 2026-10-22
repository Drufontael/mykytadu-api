package br.com.mykytadu.identity.api

import org.springframework.modulith.NamedInterface
import java.util.UUID

@NamedInterface("api")
interface IdentityAccountLifecycle {

    fun block(command: BlockAccountCommand): AccountLifecycleOutcome

    fun delete(command: DeleteAccountCommand): AccountLifecycleOutcome
}

data class BlockAccountCommand(val userId: UUID)

data class DeleteAccountCommand(val userId: UUID)

sealed interface AccountLifecycleOutcome {
    data class Blocked(val userId: UUID) : AccountLifecycleOutcome
    data class Deleted(val userId: UUID) : AccountLifecycleOutcome
    data object NotFound : AccountLifecycleOutcome
    data object AlreadyDeleted : AccountLifecycleOutcome
}
