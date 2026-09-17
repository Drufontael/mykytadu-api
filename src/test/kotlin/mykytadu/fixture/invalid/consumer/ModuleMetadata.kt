package mykytadu.fixture.invalid.consumer

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@ApplicationModule(allowedDependencies = ["allowed", "forbidden :: api"])
@PackageInfo
class ModuleMetadata
