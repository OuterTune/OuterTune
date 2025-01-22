package com.dd3boh.outertune.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dd3boh.outertune.BuildConfig
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.R
import com.dd3boh.outertune.ui.component.IconButton
import com.dd3boh.outertune.ui.utils.backToMain

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val uriHandler = LocalUriHandler.current

    val showDebugInfo = BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "userdebug"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(4.dp))

        Image(
            painter = painterResource(R.drawable.launcher_monochrome),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground, BlendMode.SrcIn),
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(NavigationBarDefaults.Elevation))
                .clickable { }
        )

        Row(
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "OuterTune",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) | ${BuildConfig.FLAVOR}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(Modifier.width(4.dp))

            if (showDebugInfo) {
                Spacer(Modifier.width(4.dp))

                Text(
                    text =  BuildConfig.BUILD_TYPE.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.secondary,
                            shape = CircleShape
                        )
                        .padding(
                            horizontal = 6.dp,
                            vertical = 2.dp
                        )
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = "By Davide Garberi & Michael Zh.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(Modifier.height(8.dp))

        Row {
            IconButton(
                onClick = { uriHandler.openUri("https://github.com/DD3Boh/OuterTune") }
            ) {
                Icon(
                    painter = painterResource(R.drawable.github),
                    contentDescription = null
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = stringResource(R.string.special_thanks),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        Text(
            text = stringResource(R.string.attrib_zhuang),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        // debug info
        if (showDebugInfo) {
            Spacer(Modifier.height(400.dp))
            Row(
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "Device info (Debug)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            val info = listOf<String>(
                "Device: ${Build.BRAND} ${Build.DEVICE} (${Build.MODEL})",
                "Manufacturer: ${Build.MANUFACTURER}",
                "HW: ${Build.BOARD} (${Build.HARDWARE})",
                "ABIs: ${Build.SUPPORTED_ABIS.joinToString()})",
                "Android: ${Build.VERSION.SDK_INT} (${Build.ID})",
                Build.DISPLAY,
                Build.PRODUCT,
                Build.FINGERPRINT,
                Build.VERSION.SECURITY_PATCH

//                needs sdk 29 or 31
//                Build.SKU ,
//                Build.ODM_SKU ,
//                Build.SOC_MODEL ,
//                Build.SOC_MANUFACTURER ,
            )

            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                info.forEach {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Red,
                    )
                }
            }
        }

    }

    TopAppBar(
        title = { Text(stringResource(R.string.about)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}
