package com.axway.maven.apigw;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.axway.maven.apigw.utils.FedBuilder;
import com.axway.maven.apigw.utils.ProjectDeploy;
import com.axway.maven.apigw.utils.ProjectPack;

@Mojo(name = "deploy", defaultPhase = LifecyclePhase.NONE, requiresProject = true, threadSafe = false)
@Execute(phase = LifecyclePhase.PACKAGE)
public class DeploymentMojo extends AbstractGatewayMojo {

	public static final String DEPLOY_DIR_NAME = "axway-deploy";

	private static final String PROJECT_NAME = "gateway";

	@Parameter(property = "axway.anm.host", required = true)
	private String anmHost;

	@Parameter(property = "axway.anm.port", required = true, defaultValue = "8090")
	private int anmPort;

	@Parameter(property = "axway.anm.user", required = true, defaultValue = "admin")
	private String anmUser;

	@Parameter(property = "axway.anm.password", required = true)
	private String anmPassword;

	@Parameter(property = "axway.deploy.group", required = true)
	private String deployGroup;

	@Parameter(property = "axway.passphrase.deploy", required = false)
	private String passphraseDeploy = null;

	public DeploymentMojo() {
	}

	private File getTempDir() {
		return new File(getTargetDir(), DEPLOY_DIR_NAME);
	}

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		PackageType pkg = getPackageType();

		switch (pkg) {
		case POLICY:
			deployPolicyProject();
			break;

		case SERVER:
			deployServerProject();
			break;

		case DEPLOYMENT:
			deployDeploymentProject();
			break;

		default:
			throw new MojoExecutionException("Unsupported package type: " + getPackageType());
		}
	}

	private void deployPolicyProject() throws MojoExecutionException {
		try {
			// pack test server project
			ProjectPack packer = new ProjectPack(this.homeAxwayGW, getLog());
			packer.setPassphrasePol(this.passphrasePol);
			int exitCode = packer.execute(getTempDir(), PROJECT_NAME, this.testServerDirectory, null);
			if (exitCode != 0) {
				throw new MojoExecutionException("failed to build packed project");
			}

			// configure fed
			File pol = new File(getTempDir(), PROJECT_NAME + ".pol");
			File env = new File(getTempDir(), PROJECT_NAME + ".env");

			File fed = configFed(pol, env);

			// deploy to server
			deployFed(fed);
		} catch (IOException e) {
			throw new MojoExecutionException("Error on packing project", e);
		}
	}

	private void deployServerProject() throws MojoExecutionException {
		File archiveBuildDir = getArchiveBuildDir();

		// configure fed
		File pol = new File(archiveBuildDir, ServerArchiveMojo.FILE_GATEWAY_POL);
		File env = new File(archiveBuildDir, ServerArchiveMojo.FILE_GATEWAY_ENV);

		File fed = configFed(pol, env);

		// deploy to server
		deployFed(fed);
	}

	private void deployDeploymentProject() throws MojoExecutionException {
		File fed = new File(getArchiveBuildDir(), DeploymentArchiveMojo.FILE_FED_NAME);

		deployFed(fed);
	}

	private File configFed(File pol, File env) throws MojoExecutionException {
		FedBuilder fc = new FedBuilder(this, pol, env, this.configConfigFile);
		fc.setPassphrasePol(this.passphrasePol);
		fc.setPassphraseFed(this.passphraseFed);
		fc.setPropertyFile(this.configPropertyFile);
		fc.setCertificatesFile(this.configCertsFile);
		fc.enableVerboseMode(this.verboseCfgTools);

		File fed = new File(getTempDir(), PROJECT_NAME + ".fed");

		int exitCode = fc.execute(fed, null);
		if (exitCode != 0) {
			throw new MojoExecutionException("failed to configure project: exitCode=" + exitCode);
		}

		return fed;
	}

	private void deployFed(File fed) throws MojoExecutionException {
		try {

			ProjectDeploy.Source source = new ProjectDeploy.Source(fed, this.passphraseFed);

			ProjectDeploy.Target target = new ProjectDeploy.Target(this.deployGroup, this.passphraseDeploy);

			ProjectDeploy deploy = new ProjectDeploy(this.homeAxwayGW, getDomain(), getLog());

			Map<String, String> polProps = new HashMap<>();
			polProps.put("Name", this.project.getGroupId() + ":" + this.project.getArtifactId());
			polProps.put("Version", this.project.getVersion());
			polProps.put("Type", "Test Deployment");

			int exitCode = deploy.execute(source, target, polProps, null);
			if (exitCode != 0) {
				throw new MojoExecutionException("Failed to deploy project: exitCode=" + exitCode);
			}
		} catch (IOException e) {
			throw new MojoExecutionException("Error on deploying project", e);
		}
	}

	private ProjectDeploy.Domain getDomain() {
		return new ProjectDeploy.Domain(this.anmHost, this.anmPort, this.anmUser, this.anmPassword);
	}
}