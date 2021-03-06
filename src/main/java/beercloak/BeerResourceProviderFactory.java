package beercloak;

import org.keycloak.Config;
import org.keycloak.models.AdminRoles;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.provider.ProviderEvent;
import org.keycloak.services.managers.RealmManager;
import org.keycloak.services.resource.RealmResourceProviderFactory;

import java.util.List;
import org.keycloak.models.utils.KeycloakModelUtils;
import static beercloak.BeerAdminAuth.ROLE_VIEW_BEER;
import static beercloak.BeerAdminAuth.ROLE_MANAGE_BEER;
import javax.persistence.EntityManager;
import org.keycloak.connections.jpa.JpaConnectionProvider;

/**
 * @author <a href="mailto:mitya@cargosoft.ru">Dmitry Telegin</a>
 */
public class BeerResourceProviderFactory implements RealmResourceProviderFactory {

    private static final String ID = "beer";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void close() {
    }

    @Override
    public BeerResourceProvider create(KeycloakSession session) {
        EntityManager em = session.getProvider(JpaConnectionProvider.class).getEntityManager();
        return new BeerResourceProvider(session, em);
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

        /*
          Due to some bug introduced in KC 2.2.x, this can cause sporadic java.sql.SQLException at startup
          (java.sql.SQLException: IJ031017: You cannot set autocommit during a managed transaction)
          Once you've had a successful startup and the roles have been created, you can comment out this block.
        */

        KeycloakModelUtils.runJobInTransaction(factory, (KeycloakSession session) -> {
            ClientModel client;
            List<RealmModel> realms = session.realms().getRealms();
            RealmManager manager = new RealmManager(session);
            for (RealmModel realm : realms) {
                client = realm.getMasterAdminClient();
                if (client.getRole(ROLE_VIEW_BEER) == null && client.getRole(ROLE_MANAGE_BEER) == null)
                    addMasterAdminRoles(manager, realm);
                if (!realm.getName().equals(Config.getAdminRealm())) {
                    client = realm.getClientByClientId(manager.getRealmAdminClientId(realm));
                    if (client.getRole(ROLE_VIEW_BEER) == null && client.getRole(ROLE_MANAGE_BEER) == null)
                        addRealmAdminRoles(manager, realm);
                }
            }
        });

        factory.register((ProviderEvent event) -> {
            if (event instanceof RealmModel.RealmPostCreateEvent) {
                RealmModel.RealmPostCreateEvent postCreate = (RealmModel.RealmPostCreateEvent) event;
                RealmModel realm = postCreate.getCreatedRealm();
                RealmManager manager = new RealmManager(postCreate.getKeycloakSession());
                addMasterAdminRoles(manager, realm);
                if (!realm.getName().equals(Config.getAdminRealm()))
                    addRealmAdminRoles(manager, realm);
            }
        });

    }

    private void addMasterAdminRoles(RealmManager manager, RealmModel realm) {

        RealmModel master = manager.getRealmByName(Config.getAdminRealm());
        RoleModel admin = master.getRole(AdminRoles.ADMIN);
        ClientModel client = realm.getMasterAdminClient();

        addRoles(client, admin);

    }

    private void addRealmAdminRoles(RealmManager manager, RealmModel realm) {

        ClientModel client = realm.getClientByClientId(manager.getRealmAdminClientId(realm));
        RoleModel admin = client.getRole(AdminRoles.REALM_ADMIN);

        addRoles(client, admin);

    }

    private void addRoles(ClientModel client, RoleModel parent) {

        String[] names = new String[] { ROLE_VIEW_BEER, ROLE_MANAGE_BEER };

        for (String name : names) {
            RoleModel role = client.addRole(name);
            role.setDescription("${role_" + name + "}");
            parent.addCompositeRole(role);
        }

    }

}
