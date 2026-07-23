from webbrowser import register

from django.contrib import admin

from user.models import User

# Register your models here.
@admin.register(User)
class UserGroupAdmin(admin.ModelAdmin):
    list_display = ["id","name"]
