package dev.jpweytjens.barberfish.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import dev.jpweytjens.barberfish.R
import dev.jpweytjens.barberfish.theme.AppTheme

@Composable
fun MainScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = stringResource(id = R.string.hello_karoo), color = MaterialTheme.colorScheme.onBackground)
    }
}

@Preview(
    widthDp = 256,
    heightDp = 426,
)
@Composable
fun DefaultPreview() {
    AppTheme {
        MainScreen()
    }
}
