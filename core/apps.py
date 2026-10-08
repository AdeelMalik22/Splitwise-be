from django.apps import AppConfig


class CoreConfig(AppConfig):
    default_auto_field = 'django.db.models.BigAutoField'
    name = 'core'

    def ready(self):
        from django.db.models.signals import post_delete

        from core.models import Group
        from user.models import User

        def drop_file(sender, instance, **kwargs):
            # Rows removed by a cascade skip model.delete(), so clean up uploaded pictures here.
            field = instance.image if sender is Group else instance.avatar
            if field:
                field.delete(save=False)

        post_delete.connect(drop_file, sender=Group, dispatch_uid='group-image-cleanup')
        post_delete.connect(drop_file, sender=User, dispatch_uid='user-avatar-cleanup')
