"""
URL configuration for splitwise project.

The `urlpatterns` list routes URLs to views. For more information please see:
    https://docs.djangoproject.com/en/5.2/topics/http/urls/
Examples:
Function views
    1. Add an import:  from my_app import views
    2. Add a URL to urlpatterns:  path('', views.home, name='home')
Class-based views
    1. Add an import:  from other_app.views import Home
    2. Add a URL to urlpatterns:  path('', Home.as_view(), name='home')
Including another URLconf
    1. Import the include() function: from django.urls import include, path
    2. Add a URL to urlpatterns:  path('blog/', include('blog.urls'))
"""
from django.contrib import admin
from django.urls import path, include, re_path
from django.views.static import serve
from django.conf import settings
from django.conf.urls.static import static
from splitwise.health import HealthCheckView
from user.views import invite_page

urlpatterns = [
    path('admin/', admin.site.urls),
    path('health/', HealthCheckView.as_view(), name='health'),
    path('invite/<str:token>/', invite_page, name='invite-page'),
    path("",include("user.urls")),
    path("",include("core.urls")),
]

# Profile and group pictures. Fine for a small deployment; put them behind a CDN or object storage if traffic grows.


def media(request, path):
    return serve(request, path, document_root=settings.MEDIA_ROOT)  # read per request so tests/deploys can change it


urlpatterns += [re_path(r'^media/(?P<path>.*)$', media)]
