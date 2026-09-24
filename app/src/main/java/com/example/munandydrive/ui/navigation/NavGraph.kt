package com.example.munandydrive.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navOptions
import com.example.munandydrive.MunandyApp
import com.example.munandydrive.ui.screens.*
import com.example.munandydrive.ui.viewmodel.AdminViewModel
import com.example.munandydrive.ui.viewmodel.AuthViewModel
import com.example.munandydrive.ui.viewmodel.CustomerViewModel
import com.example.munandydrive.ui.viewmodel.ReportsViewModel
import com.example.munandydrive.ui.viewmodel.RouteViewModel
import com.example.munandydrive.ui.viewmodel.TrackingViewModel
import com.example.munandydrive.ui.viewmodel.UserViewModel

sealed class Screen(val route: String) {
    object Splash              : Screen("splash")
    object Login               : Screen("login")
    object AdminDashboard      : Screen("admin_dashboard")
    object SupervisorDashboard : Screen("supervisor_dashboard")
    object SalesmanDashboard   : Screen("salesman_dashboard")
    object UserManagement      : Screen("user_management")
    object RouteManagement     : Screen("route_management")
    object CustomerManagement  : Screen("customer_management")
    object RouteBuilder        : Screen("route_builder?editId={editId}") {
        fun editRoute(routeId: Int) = "route_builder?editId=$routeId"
    }
    object ActiveRouteList     : Screen("active_route_list")
    object LiveTracking        : Screen("live_tracking?routeId={routeId}") {
        fun forRoute(routeId: Int) = "live_tracking?routeId=$routeId"
    }
    object MyRoutes            : Screen("my_routes")
    object ActiveRoute         : Screen("active_route/{routeId}") {
        fun createRoute(routeId: Int) = "active_route/$routeId"
    }
    object RouteDetail         : Screen("route_detail/{routeId}") {
        fun createRoute(routeId: Int) = "route_detail/$routeId"
    }
    object StopDetail          : Screen("stop_detail/{routeId}/{stopId}") {
        fun createRoute(routeId: Int, stopId: Int) = "stop_detail/$routeId/$stopId"
    }
    object RouteSummary        : Screen("route_summary/{routeId}") {
        fun createRoute(routeId: Int) = "route_summary/$routeId"
    }
    object Reports             : Screen("reports")
    object Profile             : Screen("profile")
}

@Composable
fun MunandyNavGraph(navController: NavHostController) {
    val app = LocalContext.current.applicationContext as MunandyApp

    // ViewModels — created once at NavGraph level and shared across destinations
    val authViewModel:     AuthViewModel     = viewModel(factory = AuthViewModel.Factory(app.authRepository))
    val adminViewModel:    AdminViewModel    = viewModel(factory = AdminViewModel.Factory(app.apiService))
    val userViewModel:     UserViewModel     = viewModel(factory = UserViewModel.Factory(app.userRepository))
    val customerViewModel: CustomerViewModel = viewModel(factory = CustomerViewModel.Factory(app.customerRepository))
    val routeViewModel:    RouteViewModel    = viewModel(factory = RouteViewModel.Factory(app.routeRepository))
    val trackingViewModel: TrackingViewModel = viewModel(factory = TrackingViewModel.Factory(app.apiService, app.locationHelper, app.pendingStopUpdateDao))
    val reportsViewModel:  ReportsViewModel  = viewModel(factory = ReportsViewModel.Factory(app.apiService))

    // Kick to login on 401 with a stored token
    LaunchedEffect(Unit) {
        app.tokenManager.sessionExpiredEvents.collect {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = Screen.Splash.route) {

        composable(Screen.Splash.route)  { SplashScreen(navController) }
        composable(Screen.Login.route)   { LoginScreen(navController, authViewModel) }

        composable(Screen.AdminDashboard.route) {
            AdminDashboardScreen(navController, adminViewModel)
        }
        composable(Screen.SupervisorDashboard.route) {
            SupervisorDashboardScreen(navController, routeViewModel, customerViewModel)
        }
        composable(Screen.SalesmanDashboard.route) {
            SalesmanDashboardScreen(navController, routeViewModel)
        }

        composable(Screen.UserManagement.route) {
            UserManagementScreen(navController, userViewModel)
        }
        composable(Screen.RouteManagement.route) {
            RouteManagementScreen(navController, routeViewModel, userViewModel)
        }
        composable(Screen.CustomerManagement.route) {
            CustomerManagementScreen(navController, customerViewModel)
        }
        composable(
            route = Screen.RouteBuilder.route,
            arguments = listOf(navArgument("editId") { type = NavType.IntType; defaultValue = -1 })
        ) { backStackEntry ->
            val editId = backStackEntry.arguments?.getInt("editId")?.takeIf { it != -1 }
            RouteBuilderScreen(navController, routeViewModel, userViewModel, customerViewModel, trackingViewModel, editId)
        }

        composable(Screen.ActiveRouteList.route) {
            ActiveRouteListScreen(navController, routeViewModel)
        }
        composable(
            route     = Screen.LiveTracking.route,
            arguments = listOf(navArgument("routeId") { type = NavType.IntType; defaultValue = -1 })
        ) { backStackEntry ->
            val routeId = backStackEntry.arguments?.getInt("routeId")?.takeIf { it != -1 }
            LiveTrackingScreen(
                trackingViewModel    = trackingViewModel,
                preSelectedRouteId   = routeId,
                onBack               = { navController.popBackStack() }
            )
        }
        composable(Screen.MyRoutes.route) {
            MyRoutesScreen(navController, routeViewModel)
        }
        composable(
            route     = Screen.ActiveRoute.route,
            arguments = listOf(navArgument("routeId") { type = NavType.IntType })
        ) { backStackEntry ->
            val routeId = backStackEntry.arguments?.getInt("routeId") ?: return@composable
            ActiveRouteScreen(
                routeId           = routeId,
                trackingViewModel = trackingViewModel,
                onBack            = { navController.popBackStack() },
                onViewSummary     = { navController.navigate(Screen.RouteSummary.createRoute(routeId)) }
            )
        }
        composable(
            route     = Screen.RouteDetail.route,
            arguments = listOf(navArgument("routeId") { type = NavType.IntType })
        ) { backStackEntry ->
            val routeId = backStackEntry.arguments?.getInt("routeId") ?: return@composable
            RouteDetailScreen(
                routeId           = routeId,
                navController     = navController,
                trackingViewModel = trackingViewModel,
                routeViewModel    = routeViewModel,
                userViewModel     = userViewModel
            )
        }

        composable(
            route     = Screen.StopDetail.route,
            arguments = listOf(
                navArgument("routeId") { type = NavType.IntType },
                navArgument("stopId")  { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val routeId = backStackEntry.arguments?.getInt("routeId") ?: return@composable
            val stopId  = backStackEntry.arguments?.getInt("stopId")  ?: return@composable
            StopDetailScreen(navController, routeId, stopId, trackingViewModel)
        }
        composable(
            route     = Screen.RouteSummary.route,
            arguments = listOf(navArgument("routeId") { type = NavType.IntType })
        ) { backStackEntry ->
            val routeId = backStackEntry.arguments?.getInt("routeId") ?: return@composable
            RouteSummaryScreen(navController, routeId, trackingViewModel)
        }
        composable(Screen.Reports.route)      { ReportsScreen(navController, reportsViewModel) }
        composable(Screen.Profile.route)      { ProfileScreen(navController) }
    }
}
