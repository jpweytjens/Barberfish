package com.jpweytjen.barberfish.datatype

import io.hammerhead.karooext.KarooSystemService



class CustomRollingType(
    karooSystem: KarooSystemService,

    datatype: String,
    value: Int
) : CustomRollingTypeBase(karooSystem,  datatype,value)